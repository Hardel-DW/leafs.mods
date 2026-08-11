package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.network.PlayerListFileAccess;
import fr.hardel.leafs.network.PlayerTeardown;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Concurrent player lists for cross-region reads; placement runs on the owning unit, removal on the unit that owns the player. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin implements PlayerListFileAccess {

    @Mutable
    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, ServerPlayer> playersByUUID;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, ServerStatsCounter> stats;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, PlayerAdvancements> advancements;

    @Shadow
    public abstract MinecraftServer getServer();

    @Shadow
    private Path locateStatsFile(GameProfile gameProfile) {
        throw new IllegalStateException("Shadowed method body");
    }

    @Override
    public Path leafs$statsFile(GameProfile profile) {
        return locateStatsFile(profile);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPlayerLists(CallbackInfo callbackInfo) {
        this.players = new CopyOnWriteArrayList<>();
        this.playersByUUID = new ConcurrentHashMap<>();
        this.stats = new ConcurrentHashMap<>();
        this.advancements = new ConcurrentHashMap<>();
    }

    /** Server-thread placement runs inline; the level mutation inside takes the exclusion at {@code ServerLevel.addPlayer}. */
    @Inject(method = "placeNewPlayer", at = @At("HEAD"), cancellable = true)
    private void leafs$placeOnOwningUnit(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo callbackInfo) {
        TickingManager ticking = ((LeafsServerAccess) this.getServer()).leafs$ticking();
        if (ticking.currentThreadOwns(player.level()) || this.getServer().isSameThread()) {
            return;
        }

        ticking.submitToLevel(player.level(), () -> ((PlayerList) (Object) this).placeNewPlayer(connection, player, cookie));
        callbackInfo.cancel();
    }

    /** Roadmap 22 instrumentation: a placement that stalls the level thread must name itself. */
    @WrapMethod(method = "placeNewPlayer")
    private void leafs$timedPlacement(Connection connection, ServerPlayer player, CommonListenerCookie cookie, Operation<Void> original) {
        long start = System.nanoTime();
        original.call(connection, player, cookie);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (millis > 100) {
            Leafs.LOGGER.warn("Placing {} took {} ms on the level thread", player.getPlainTextName(), millis);
        }
    }

    /** Autosave snapshots a live player from the global thread; the owning level's exclusion orders it against region ticks. */
    @WrapMethod(method = "save")
    private void leafs$savePlayerUnderExclusion(ServerPlayer player, Operation<Void> original) {
        if (player.level() instanceof ServerLevel level) {
            ((ServerLevelRegionAccess) level).leafs$regions().ownership().runExclusive(() -> original.call(player));
        } else {
            original.call(player);
        }
    }

    /** The vanilla removal body runs whole under the pause of every region; disk writes leave on the deferred thread. */
    @WrapMethod(method = "remove")
    private void leafs$teardownUnderRegionPause(ServerPlayer player, Operation<Void> original) {
        PlayerTeardown.remove((PlayerList) (Object) this, player, () -> original.call(player));
    }
}
