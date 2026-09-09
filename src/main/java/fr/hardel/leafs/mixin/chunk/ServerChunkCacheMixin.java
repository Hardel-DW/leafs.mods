package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.minecraft.core.SectionPos;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

/** The read contract: every thread reads what is published, a required absent chunk is waited for, a borrower takes the chunk's region. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Thread mainThread;

    /** Off the main thread the table answers, with the contract's peek rule: presence serves every thread. */
    @Inject(method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void leafs$concurrentReadPath(int x, int z, CallbackInfoReturnable<LevelChunk> callbackInfo) {
        if (Thread.currentThread() != this.mainThread) {
            callbackInfo.setReturnValue(RegionChunkAccess.fullChunkOrNull(leafs$chunkMap(), x, z));
        }
    }

    /** The contract for every thread once regions run, vanilla for the universal owner. */
    @WrapMethod(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;")
    private ChunkAccess leafs$contractedGetChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, Operation<ChunkAccess> original) {
        LevelRegions regions = LevelRegions.of(this.level);
        ChunkAccess chunk = regions.live() ? RegionChunkAccess.contractedChunk(leafs$chunkMap(), x, z, targetStatus, loadOrGenerate) : original.call(x, z, targetStatus, loadOrGenerate);
        if (chunk != null) {
            RegionBorrow.atContact(regions, x, z);
        }

        return chunk;
    }

    /** Vanilla answers from the ticket level; the read path answers from presence. Both must agree on every thread. */
    @Inject(method = "hasChunk(II)Z", at = @At("HEAD"), cancellable = true)
    private void leafs$concurrentHasChunkPath(int x, int z, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (LevelRegions.of(this.level).live()) {
            callbackInfo.setReturnValue(RegionChunkAccess.fullChunkOrNull(leafs$chunkMap(), x, z) != null);
        }
    }

    /** A light change marks its section on the chunk's owner, like a block change; the owner broadcasts at its next pass. */
    @WrapOperation(method = "onLightUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;execute(Ljava/lang/Runnable;)V"))
    private void leafs$lightChangeOnTheOwner(ServerChunkCache.MainThreadExecutor pump, Runnable mark, Operation<Void> original, @Local(argsOnly = true) SectionPos pos) {
        LevelChunks.of(this.level).owners().submit(pos.x(), pos.z(), Work.CHUNK, mark);
    }

    /** Vanilla reads the holder right after runAllUpdates: the chunk's own section settles on this thread. */
    @WrapOperation(method = "getChunkFutureMainThread", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z"))
    private boolean leafs$settleTheRequestedChunk(ServerChunkCache self, Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int z) {
        boolean changed = original.call(self);
        LevelChunks.of(this.level).holders().settle(x, z);
        return changed;
    }

    @WrapOperation(method = "addTicketAndLoadWithRadius", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z"))
    private boolean leafs$settleTheRadiusCentre(ServerChunkCache self, Operation<Boolean> original, @Local(argsOnly = true) ChunkPos pos) {
        boolean changed = original.call(self);
        LevelChunks.of(this.level).holders().settle(pos.x(), pos.z());
        return changed;
    }

    /** A request writes holder state: under the graph's locks, once the chunk's tickets settled. */
    @WrapOperation(method = "getChunkFutureMainThread", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder;scheduleChunkGenerationTask(Lnet/minecraft/world/level/chunk/status/ChunkStatus;Lnet/minecraft/server/level/ChunkMap;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> leafs$requestUnderTheGraphLocks(ChunkHolder holder, ChunkStatus status, ChunkMap chunkMap, Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        ChunkPos pos = holder.getPos();
        return LevelChunks.of(this.level).holders().settled(pos.x(), pos.z(), () -> original.call(holder, status, chunkMap));
    }

    @WrapOperation(method = "addTicketAndLoadWithRadius", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getChunkRangeFuture(Lnet/minecraft/server/level/ChunkHolder;ILjava/util/function/IntFunction;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<List<ChunkAccess>>> leafs$radiusRequestUnderTheGraphLocks(ChunkMap chunkMap, ChunkHolder holder, int radius, IntFunction<ChunkStatus> distanceToStatus, Operation<CompletableFuture<ChunkResult<List<ChunkAccess>>>> original) {
        ChunkPos pos = holder.getPos();
        return LevelChunks.of(this.level).holders().settled(pos.x(), pos.z(), () -> original.call(chunkMap, holder, radius, distanceToStatus));
    }

    @Unique
    private ChunkMap leafs$chunkMap() {
        return ((ServerChunkCache) (Object) this).chunkMap;
    }
}
