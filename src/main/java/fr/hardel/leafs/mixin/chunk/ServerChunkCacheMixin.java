package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.ChunkSystemThread;
import fr.hardel.leafs.chunk.ChunkThreadAccess;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$startChunkThread(CallbackInfo callbackInfo) {
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        this.leafs$chunkThread = new ChunkSystemThread(self, level.dimension().identifier().toString());
        this.mainThread = leafs$chunkThread.start();
        ((TicketStorageAccess) self.ticketStorage).leafs$bindChunkExecutor(self.mainThreadProcessor);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void leafs$tickOnChunkThread(BooleanSupplier haveTime, boolean tickChunks, CallbackInfo callbackInfo) {
        if (!leafs$chunkThread.isCurrentThread()) {
            leafs$chunkThread.runBlocking(() -> ((ServerChunkCache) (Object) this).tick(haveTime, tickChunks));
            callbackInfo.cancel();
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void leafs$saveOnChunkThread(boolean flushStorage, CallbackInfo callbackInfo) {
        if (!leafs$chunkThread.isCurrentThread()) {
            leafs$chunkThread.runBlocking(() -> ((ServerChunkCache) (Object) this).save(flushStorage));
            callbackInfo.cancel();
        }
    }

    @Inject(method = "deactivateTicketsOnClosing", at = @At("HEAD"), cancellable = true)
    private void leafs$deactivateOnChunkThread(CallbackInfo callbackInfo) {
        if (!leafs$chunkThread.isCurrentThread()) {
            leafs$chunkThread.runBlocking(((ServerChunkCache) (Object) this)::deactivateTicketsOnClosing);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void leafs$closeOnChunkThread(CallbackInfo callbackInfo) throws java.io.IOException {
        if (!leafs$chunkThread.isCurrentThread()) {
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
        if (Thread.currentThread() == this.mainThread) {
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
}
