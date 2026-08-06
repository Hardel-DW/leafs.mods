package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Concurrent player lists for cross-region reads; placement runs on the owning unit. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Mutable
    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Mutable
    @Shadow
    @Final
    private Map<UUID, ServerPlayer> playersByUUID;

    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPlayerLists(CallbackInfo callbackInfo) {
        this.players = new CopyOnWriteArrayList<>();
        this.playersByUUID = new ConcurrentHashMap<>();
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

    /** Autosave snapshots a live player from the global thread; the owning level's exclusion orders it against region ticks. */
    @WrapMethod(method = "save")
    private void leafs$savePlayerUnderExclusion(ServerPlayer player, Operation<Void> original) {
        if (player.level() instanceof ServerLevel level) {
            ((ServerLevelRegionAccess) level).leafs$regions().ownership().runExclusive(() -> original.call(player));
        } else {
            original.call(player);
        }
    }

    /** Teardown reaches entities other regions own (unRide, the pearl sweep, cross-level); it runs with every region paused. */
    @WrapMethod(method = "remove")
    private void leafs$teardownWithRegionsPaused(ServerPlayer player, Operation<Void> original) {
        ((LeafsServerAccess) this.getServer()).leafs$ticking().runWithRegionsPaused(() -> original.call(player));
    }
}
