package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.chunk.TicketTimeoutIndex;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.TickingBinding;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Per-chunk tick work moved to region bodies; broadcast marks route to the owning unit; player moves defer to serial. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    private boolean spawnEnemies;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$bindPumpLevel(CallbackInfo callbackInfo) {
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        ((ChunkPumpAccess) (Object) self.mainThreadProcessor).leafs$bindLevel(this.level);
    }

    /** Once regions tick, each purges its own sections; the serial phase keeps only the sections no region owns, never the whole table. */
    @WrapOperation(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/TicketStorage;purgeStaleTickets(Lnet/minecraft/server/level/ChunkMap;)V"))
    private void leafs$purgeUnownedTimeouts(TicketStorage storage, ChunkMap chunkMap, Operation<Void> original) {
        LevelRegions regions = LevelRegions.of(this.level);
        if (regions.body() == null) {
            original.call(storage, chunkMap);
            return;
        }

        TicketTimeoutIndex timeouts = ((TicketStorageAccess) storage).leafs$timeouts();
        if (timeouts == null || timeouts.isEmpty()) {
            return;
        }

        Regionizer<RegionTickData> regionizer = regions.regionizer();
        int chunkShift = regionizer.sectionShift();
        timeouts.purgeUnowned(section -> regionizer.regionAt(CoordinateKey.x(section) << chunkShift, CoordinateKey.z(section) << chunkShift) != null);
    }

    @WrapOperation(method = "tickChunks()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V"))
    private void leafs$serialChunkTickRemainder(ServerChunkCache instance, ProfilerFiller profiler, long timeDiff, Operation<Void> original) {
        RegionTickBody body = LevelRegions.of(this.level).body();
        if (body == null) {
            original.call(instance, profiler, timeDiff);

            return;
        }

        body.tickSerialRemainder(this.spawnEnemies);
        TickingManager.of(this.level.getServer()).markSerial(this.level, TickStages.serialView);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z", shift = At.Shift.AFTER), require = 0)
    private void leafs$markPurgeStage(CallbackInfo callbackInfo) {
        TickingManager.of(this.level.getServer()).markSerial(this.level, TickStages.serialPurge);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;tick(Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.AFTER), require = 0)
    private void leafs$markUnloadsStage(CallbackInfo callbackInfo) {
        TickingManager.of(this.level.getServer()).markSerial(this.level, TickStages.serialUnloads);
    }

    @WrapOperation(method = {"blockChanged", "onChunkReadyToSend"}, at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean leafs$markOwnedBroadcastSet(Set<ChunkHolder> instance, Object holder, Operation<Boolean> original) {
        RegionWorldData data = WorldTickContext.activeFor(this.level);
        if (data == null) {
            return original.call(instance, holder);
        }

        return data.broadcastHolders().add((ChunkHolder) holder);
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void leafs$deferPlayerMoveToLevelSerial(ServerPlayer player, CallbackInfo callbackInfo) {
        LevelRegions regions = LevelRegions.of(this.level);
        if (regions.body() == null || regions.ownership().isLevelSerialHeldByCurrentThread()) {
            return;
        }

        ServerChunkCache self = (ServerChunkCache) (Object) this;
        DeferredTransports transports = TickingBinding.of(this.level);
        DeferredWork.serial(DeferReason.PLAYER_MOVE, transports.stats(), () -> self.move(player))
            .validIf(() -> !player.isRemoved() && player.level() == this.level)
            .submit(transports);
        callbackInfo.cancel();
    }
}
