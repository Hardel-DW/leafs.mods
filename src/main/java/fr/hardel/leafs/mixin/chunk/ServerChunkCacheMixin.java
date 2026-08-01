package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.ChunkSystemThread;
import fr.hardel.leafs.chunk.ChunkThreadAccess;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * The executor identity re-point: a dedicated thread owns the chunk bookkeeping. Bookkeeping entry
 * points called by game threads hop (blocking, vanilla order preserved); loaded-chunk reads stay
 * direct through the double-buffered visible map, bypassing the chunk-thread-only 4-slot cache.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin implements ChunkThreadAccess {

    @Mutable
    @Shadow
    @Final
    private Thread mainThread;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract ChunkHolder getVisibleChunkIfPresent(long pos);

    @Unique
    private ChunkSystemThread leafs$chunkThread;

    @Override
    public ChunkSystemThread leafs$chunkThread() {
        return leafs$chunkThread;
    }

    @Mutable
    @Shadow
    @Final
    private Set<ChunkHolder> chunkHoldersToBroadcast;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$startChunkThread(CallbackInfo callbackInfo) {
        if (!LeafsConfig.get().chunkThreads()) {
            return;
        }

        ServerChunkCache self = (ServerChunkCache) (Object) this;
        this.chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();
        this.leafs$chunkThread = new ChunkSystemThread(self, level.dimension().identifier().toString());
        this.mainThread = leafs$chunkThread.start();
        ((TicketStorageAccess) self.ticketStorage).leafs$bindChunkExecutor(self.mainThreadProcessor);
    }

    /** Game threads add holders while the chunk thread drains: remove-as-you-go so a concurrent add is never wiped by the trailing clear. */
    @Inject(method = "broadcastChangedChunks", at = @At("HEAD"), cancellable = true)
    private void leafs$lossFreeBroadcastDrain(ProfilerFiller profiler, CallbackInfo callbackInfo) {
        if (leafs$chunkThread == null) {
            return;
        }

        profiler.push("broadcast");
        for (Iterator<ChunkHolder> iterator = chunkHoldersToBroadcast.iterator(); iterator.hasNext(); ) {
            ChunkHolder holder = iterator.next();
            iterator.remove();
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk != null) {
                holder.broadcastChanges(chunk);
            }
        }
        profiler.pop();
        callbackInfo.cancel();
    }

    /** Vanilla calls this directly from login spawn preparation (ChunkLoadCounter.track) on game threads. */
    @Inject(method = "runDistanceManagerUpdates", at = @At("HEAD"), cancellable = true)
    private void leafs$distanceUpdatesOnChunkThread(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (leafs$chunkThread != null && !leafs$chunkThread.isCurrentThread()) {
            callbackInfo.setReturnValue(leafs$chunkThread.supplyBlocking(((ServerChunkCache) (Object) this)::runDistanceManagerUpdates));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void leafs$tickOnChunkThread(BooleanSupplier haveTime, boolean tickChunks, CallbackInfo callbackInfo) {
        if (leafs$chunkThread != null && !leafs$chunkThread.isCurrentThread()) {
            leafs$chunkThread.runBlocking(() -> ((ServerChunkCache) (Object) this).tick(haveTime, tickChunks));
            callbackInfo.cancel();
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void leafs$saveOnChunkThread(boolean flushStorage, CallbackInfo callbackInfo) {
        if (leafs$chunkThread != null && !leafs$chunkThread.isCurrentThread()) {
            leafs$chunkThread.runBlocking(() -> ((ServerChunkCache) (Object) this).save(flushStorage));
            callbackInfo.cancel();
        }
    }

    @Inject(method = "deactivateTicketsOnClosing", at = @At("HEAD"), cancellable = true)
    private void leafs$deactivateOnChunkThread(CallbackInfo callbackInfo) {
        if (leafs$chunkThread != null && !leafs$chunkThread.isCurrentThread()) {
            leafs$chunkThread.runBlocking(((ServerChunkCache) (Object) this)::deactivateTicketsOnClosing);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void leafs$closeOnChunkThread(CallbackInfo callbackInfo) throws java.io.IOException {
        if (leafs$chunkThread != null && !leafs$chunkThread.isCurrentThread()) {
            leafs$chunkThread.runBlocking(() -> {
                try {
                    ((ServerChunkCache) (Object) this).close();
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
            leafs$chunkThread.stop();
            callbackInfo.cancel();
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void leafs$directReadForGameThreads(int x, int z, CallbackInfoReturnable<LevelChunk> callbackInfo) {
        if (leafs$chunkThread == null || Thread.currentThread() == this.mainThread) {
            return;
        }

        if (RegionContext.current() == null && Thread.currentThread() != level.getServer().getRunningThread()) {
            callbackInfo.setReturnValue(null);
            return;
        }

        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(ChunkStatus.FULL);
        callbackInfo.setReturnValue(chunk instanceof LevelChunk levelChunk ? levelChunk : null);
    }

    /** THE hot path: every block read reaches getChunk; loaded FULL chunks must never pay a chunk-thread round trip. */
    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void leafs$directLoadedChunkRead(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<ChunkAccess> callbackInfo) {
        if (leafs$chunkThread == null || Thread.currentThread() == this.mainThread || targetStatus != ChunkStatus.FULL) {
            return;
        }

        if (RegionContext.current() == null && Thread.currentThread() != level.getServer().getRunningThread()) {
            return;
        }

        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(ChunkStatus.FULL);
        if (chunk instanceof LevelChunk) {
            callbackInfo.setReturnValue(chunk);
        }
    }
}
