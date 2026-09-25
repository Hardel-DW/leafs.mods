package fr.hardel.leafs.chunk.disk;

import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ChunkWrites {
    private final ChunkPool pool;
    private final IOWorker disk;
    private final RegionFileStorage files;
    private final ConcurrentHashMap<Long, PendingWrite> pending = new ConcurrentHashMap<>();

    public ChunkWrites(ChunkPool pool, IOWorker disk) {
        this.pool = pool;
        this.disk = disk;
        this.files = disk.storage;
    }

    public PendingWrite photograph(ChunkPos pos, Supplier<CompoundTag> photo) {
        PendingWrite write = new PendingWrite(photo);
        pending.put(pos.pack(), write);
        write.completeAsync(() -> compress(write, photo), pool);
        write.thenCompose(_ -> store(pos, write)).whenComplete((_, failure) -> finish(pos, write, failure));
        return write;
    }

    public @Nullable PendingWrite pending(ChunkPos pos) {
        return pending.get(pos.pack());
    }

    public CompletableFuture<Void> settled() {
        return CompletableFuture.allOf(pending.values().stream().map(PendingWrite::written).toArray(CompletableFuture[]::new));
    }

    private static CompoundTag compress(PendingWrite write, Supplier<CompoundTag> photo) {
        CompoundTag tag = photo.get();
        write.compressed(CompressedChunk.of(tag));
        return tag;
    }

    private CompletableFuture<Void> store(ChunkPos pos, PendingWrite write) {
        return disk.consecutiveExecutor.scheduleWithResult(IOWorker.Priority.BACKGROUND.ordinal(), stored -> storeOnDisk(pos, write, stored));
    }

    private void storeOnDisk(ChunkPos pos, PendingWrite write, CompletableFuture<Void> stored) {
        try {
            writeIfStillPending(pos, write);
            stored.complete(null);
        } catch (Exception exception) {
            stored.completeExceptionally(exception);
        }
    }

    private void writeIfStillPending(ChunkPos pos, PendingWrite write) throws IOException {
        if (pending.get(pos.pack()) != write) {
            return;
        }

        CompressedChunk bytes = write.bytes();
        RegionFile file = files.getRegionFile(pos);
        JvmProfiler.INSTANCE.onRegionFileWrite(files.info(), pos, bytes.version(), bytes.streamLength());
        file.write(pos, bytes.buffer());
    }

    private void finish(ChunkPos pos, PendingWrite write, @Nullable Throwable failure) {
        pending.remove(pos.pack(), write);
        if (failure != null) {
            write.written().completeExceptionally(failure);
            return;
        }

        write.written().complete(null);
    }
}
