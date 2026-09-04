package fr.hardel.leafs.chunk.disk;

import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The chunks of a level on their way to the disk: the pool photographs and compresses, the disk thread only writes bytes. */
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

    /** One pool task, photo then compression, then the file write on the disk thread. The write is vanilla's photo future. */
    public PendingWrite photograph(ChunkPos pos, Supplier<CompoundTag> photo) {
        PendingWrite write = new PendingWrite(photo);
        pending.put(pos.pack(), write);
        CompletableFuture.supplyAsync(() -> {
            CompoundTag tag = photo.get();
            write.compressed(CompressedChunk.of(tag));
            return tag;
        }, pool).whenComplete((tag, failure) -> {
            if (failure == null) {
                write.complete(tag);
            } else {
                write.completeExceptionally(failure);
            }
        });
        write.thenCompose(_ -> store(pos, write.bytes())).whenComplete((_, failure) -> {
            pending.remove(pos.pack(), write);
            if (failure == null) {
                write.written().complete(null);
            } else {
                write.written().completeExceptionally(failure);
            }
        });
        return write;
    }

    /** Null once the bytes are in the file. */
    public @Nullable PendingWrite pending(ChunkPos pos) {
        return pending.get(pos.pack());
    }

    /** For a flush of the storage. */
    public CompletableFuture<Void> settled() {
        return CompletableFuture.allOf(pending.values().stream().map(PendingWrite::written).toArray(CompletableFuture[]::new));
    }

    /** Vanilla's ChunkBuffer close, on the disk thread. */
    private CompletableFuture<Void> store(ChunkPos pos, CompressedChunk bytes) {
        return disk.submitThrowingTask(() -> {
            RegionFile file = files.getRegionFile(pos);
            JvmProfiler.INSTANCE.onRegionFileWrite(files.info(), pos, bytes.version(), bytes.streamLength());
            file.write(pos, bytes.buffer());
            return null;
        });
    }
}
