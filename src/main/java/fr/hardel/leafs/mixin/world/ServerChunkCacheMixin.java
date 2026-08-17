package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.metrics.SerialStage;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
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

    @WrapOperation(method = "tickChunks()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V"))
    private void leafs$serialChunkTickRemainder(ServerChunkCache instance, ProfilerFiller profiler, long timeDiff, Operation<Void> original) {
        RegionTickBody body = LevelRegions.of(this.level).body();
        if (body == null) {
            original.call(instance, profiler, timeDiff);

            return;
        }

        body.tickSerialRemainder(this.spawnEnemies);
        TickingManager.of(this.level.getServer()).markSerial(this.level, SerialStage.VIEW);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z", shift = At.Shift.AFTER))
    private void leafs$markPurgeStage(CallbackInfo callbackInfo) {
        TickingManager.of(this.level.getServer()).markSerial(this.level, SerialStage.PURGE);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;tick(Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.AFTER))
    private void leafs$markUnloadsStage(CallbackInfo callbackInfo) {
        TickingManager.of(this.level.getServer()).markSerial(this.level, SerialStage.UNLOADS);
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
        TickingManager.of(this.level.getServer()).submitToLevel(this.level, () -> {
            if (!player.isRemoved() && player.level() == this.level) {
                self.move(player);
            }
        });
        callbackInfo.cancel();
    }
}
