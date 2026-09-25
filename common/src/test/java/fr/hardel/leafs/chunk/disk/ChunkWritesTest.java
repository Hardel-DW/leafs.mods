package fr.hardel.leafs.chunk.disk;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
@ExtendWith(MinecraftBootstrap.class)
class ChunkWritesTest {
    private final ChunkPool pool = ChunkFixtures.pool(2);
    private final SimpleRegionStorage storage;

    ChunkWritesTest() throws IOException {
        storage = new SimpleRegionStorage(new RegionStorageInfo("test", Level.OVERWORLD, "chunk"), Files.createTempDirectory("leafs-writes"), DataFixers.getDataFixer(), false, DataFixTypes.CHUNK);
    }

    @AfterEach
    void stop() throws IOException {
        pool.shutdown();
        storage.close();
    }

    /** B01: the older photo of a chunk finished encoding after the newer one and wrote its stale state over it; the disk keeps the last photo, like vanilla's write queue. */
    @Test
    void anOlderPhotoFinishingAfterANewerOneIsNeverWritten() throws InterruptedException {
        ChunkWrites writes = new ChunkWrites(pool, storage.worker);
        ChunkPos pos = new ChunkPos(3, 4);
        CountDownLatch olderTaken = new CountDownLatch(1);
        CountDownLatch newerWritten = new CountDownLatch(1);

        PendingWrite older = writes.photograph(pos, () -> {
            olderTaken.countDown();
            TestThreads.await(newerWritten);
            return ChunkFixtures.photo(1);
        });
        olderTaken.await(5, TimeUnit.SECONDS);
        PendingWrite newer = writes.photograph(pos, () -> ChunkFixtures.photo(2));
        newer.written().join();
        newerWritten.countDown();
        older.written().join();
        writes.settled().join();

        CompoundTag onDisk = storage.worker.loadAsync(pos).join().orElseThrow();
        assertEquals(2, onDisk.getIntOr("DataVersion", 0), "the disk keeps the last photo");
    }

    /** 2026-09-24: the chunk bytes went to the disk in the foreground, so a chunk read waited behind every queued write. */
    @Test
    void aChunkReadPassesBeforeTheQueuedWrites() {
        ChunkWrites writes = new ChunkWrites(pool, storage.worker);
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch disk = TestThreads.occupy(task -> storage.worker.submitThrowingTask(() -> {
            task.run();
            return null;
        }));

        CompletableFuture<Void> first = writes.photograph(new ChunkPos(0, 0), () -> ChunkFixtures.photo(1)).written().thenRun(() -> order.add("write"));
        CompletableFuture<Void> second = writes.photograph(new ChunkPos(1, 0), () -> ChunkFixtures.photo(1)).written().thenRun(() -> order.add("write"));
        while (storage.worker.consecutiveExecutor.size() < 2) {
            Thread.onSpinWait();
        }

        CompletableFuture<Void> read = storage.worker.loadAsync(new ChunkPos(5, 5)).thenRun(() -> order.add("read"));
        disk.countDown();
        CompletableFuture.allOf(first, second, read).join();

        assertEquals(List.of("read", "write", "write"), order);
    }
}
