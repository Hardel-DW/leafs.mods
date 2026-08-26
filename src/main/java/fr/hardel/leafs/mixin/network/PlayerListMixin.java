package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.network.PlayerListFileAccess;
import fr.hardel.leafs.network.PlayerTeardown;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.TickingBinding;
import net.minecraft.network.Connection;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
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

    /** The placement runs on the region owning the spawn chunk, materialised by the queue if needed. */
    @Inject(method = "placeNewPlayer", at = @At("HEAD"), cancellable = true)
    private void leafs$placeOnSpawnOwner(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo callbackInfo) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        ChunkPos spawnChunk = player.chunkPosition();
        DeferredTransports transports = TickingBinding.of(level);
        if (transports.owns(spawnChunk.x(), spawnChunk.z())) {
            return;
        }

        DeferredWork.owner(DeferReason.PLAYER_PLACEMENT, transports.stats(), spawnChunk.x(), spawnChunk.z(),
            () -> ((PlayerList) (Object) this).placeNewPlayer(connection, player, cookie)).submit(transports);
        callbackInfo.cancel();
    }

    /** A placement that stalls its owning thread must name itself; the queueing half never crosses the threshold. */
    @WrapMethod(method = "placeNewPlayer")
    private void leafs$timedPlacement(Connection connection, ServerPlayer player, CommonListenerCookie cookie, Operation<Void> original) {
        long start = System.nanoTime();
        original.call(connection, player, cookie);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        int threshold = LeafsConfig.get().debug().slowTaskWarnMillis();
        if (threshold > 0 && millis > threshold) {
            Leafs.LOGGER.warn("Placing {} took {} ms on its owner", player.getPlainTextName(), millis);
        }
    }

    /** A direct call from elsewhere than the disconnect still lands on the player's owner. */
    @WrapMethod(method = "remove")
    private void leafs$removeOnTheOwner(ServerPlayer player, Operation<Void> original) {
        PlayerTeardown.run(player, () -> original.call(player));
    }

    /** A pearl may fly in another region or dimension: its removal runs on that owner. */
    @WrapOperation(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/throwableitemprojectile/ThrownEnderpearl;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void leafs$removePearlOnItsOwner(ThrownEnderpearl pearl, Entity.RemovalReason reason, Operation<Void> original) {
        if (!(pearl.level() instanceof ServerLevel level)) {
            original.call(pearl, reason);
            return;
        }

        ChunkPos chunk = pearl.chunkPosition();
        TickingBinding.of(level).toOwner(chunk.x(), chunk.z(), () -> original.call(pearl, reason));
    }
}
