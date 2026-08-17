package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.ticking.LevelOwnership;
import fr.hardel.leafs.ticking.LevelRegions;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

/** Region workers answer thread-identity checks through ownership: mid-tick is a game thread for its level. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Thread mainThread;

    /** The storage is constructed level-blind as saved data; the routing shim needs its level. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$bindTicketStorage(CallbackInfo callbackInfo) {
        ((TicketStorageAccess) ((ServerChunkCache) (Object) this).ticketStorage).leafs$bindLevel(this.level);
    }

    /** Any thread reads published FULL chunks through the concurrent table; vanilla answered null off its main thread. */
    @Inject(method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void leafs$concurrentReadPath(int x, int z, CallbackInfoReturnable<LevelChunk> callbackInfo) {
        if (DegradedChunkReads.active() || Thread.currentThread() != this.mainThread) {
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            callbackInfo.setReturnValue(RegionChunkAccess.fullChunkOrNull(self.chunkMap, x, z));
        }
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void leafs$regionGetChunkPath(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<ChunkAccess> callbackInfo) {
        if (leafs$degradedReadPath()) {
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            callbackInfo.setReturnValue(RegionChunkAccess.presentChunkOrThrow(self.chunkMap, x, z, targetStatus, loadOrGenerate));
        }
    }

    /** Vanilla answers from the ticket level; the read path answers from presence. Both must agree or a correct hasChunk-then-read sequence crashes. */
    @Inject(method = "hasChunk(II)Z", at = @At("HEAD"), cancellable = true)
    private void leafs$concurrentHasChunkPath(int x, int z, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DegradedChunkReads.active() || Thread.currentThread() != this.mainThread) {
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            callbackInfo.setReturnValue(RegionChunkAccess.fullChunkOrNull(self.chunkMap, x, z) != null);
        }
    }

    /** A region worker mid-tick, or a serial scope that opted into region-like reads (custom spawners). */
    @Unique
    private boolean leafs$degradedReadPath() {
        if (DegradedChunkReads.active()) {
            return true;
        }

        if (Thread.currentThread() == this.mainThread) {
            return false;
        }

        LevelOwnership ownership = LevelRegions.of(this.level).ownership();

        return ownership.isRegionTickHeldByCurrentThread();
    }

    /** The request takes the scheduling area around the position, and the tasks it builds start after the release. */
    @WrapOperation(method = "getChunkFutureMainThread", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder;scheduleChunkGenerationTask(Lnet/minecraft/world/level/chunk/status/ChunkStatus;Lnet/minecraft/server/level/ChunkMap;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> leafs$requestUnderSchedulingLock(ChunkHolder holder, ChunkStatus status, ChunkMap chunkMap, Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        ChunkPos pos = holder.getPos();
        return leafs$scheduling().requestArea(pos.x(), pos.z(), 0, () -> original.call(holder, status, chunkMap));
    }

    /** Same discipline for the radius form, which schedules a whole area of neighbours at once. */
    @WrapOperation(method = "addTicketAndLoadWithRadius", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getChunkRangeFuture(Lnet/minecraft/server/level/ChunkHolder;ILjava/util/function/IntFunction;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<List<ChunkAccess>>> leafs$radiusRequestUnderSchedulingLock(ChunkMap chunkMap, ChunkHolder holder, int radius, IntFunction<ChunkStatus> distanceToStatus, Operation<CompletableFuture<ChunkResult<List<ChunkAccess>>>> original) {
        ChunkPos pos = holder.getPos();
        return leafs$scheduling().requestArea(pos.x(), pos.z(), radius, () -> original.call(chunkMap, holder, radius, distanceToStatus));
    }

    @Unique
    private ChunkScheduling leafs$scheduling() {
        return ((PropagatorAccess) ((ServerChunkCache) (Object) this).chunkMap.getDistanceManager()).leafs$propagator().scheduling();
    }
}
