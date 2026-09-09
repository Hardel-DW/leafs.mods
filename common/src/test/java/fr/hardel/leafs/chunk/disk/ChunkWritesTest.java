package fr.hardel.leafs.chunk.disk;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.SharedConstants;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
@ExtendWith(MinecraftBootstrap.class)
class ChunkWritesTest {
    private final ChunkPool pool = new ChunkPool(Thread.currentThread().getThreadGroup(), 2, 4);
    private SimpleRegionStorage storage;

    @AfterEach
    void stop() throws IOException {
        pool.shutdown();
        storage.close();
    }

    /** B01: the older photo of a chunk finished encoding after the newer one and wrote its stale state over it; the disk keeps the last photo, like vanilla's write queue. */
    @Test
    void anOlderPhotoFinishingAfterANewerOneIsNeverWritten() throws IOException, InterruptedException {
        storage = new SimpleRegionStorage(new RegionStorageInfo("test", Level.OVERWORLD, "chunk"), Files.createTempDirectory("leafs-writes"), DataFixers.getDataFixer(), false, DataFixTypes.CHUNK);
        ChunkWrites writes = new ChunkWrites(pool, storage.worker);
        ChunkPos pos = new ChunkPos(3, 4);
        CountDownLatch olderTaken = new CountDownLatch(1);
        CountDownLatch newerWritten = new CountDownLatch(1);

        PendingWrite older = writes.photograph(pos, () -> {
            olderTaken.countDown();
            await(newerWritten);
            return photo(1);
        });
        olderTaken.await(5, TimeUnit.SECONDS);
        PendingWrite newer = writes.photograph(pos, () -> photo(2));
        newer.written().join();
        newerWritten.countDown();
        older.written().join();
        writes.settled().join();

        CompoundTag onDisk = storage.worker.loadAsync(pos).join().orElseThrow();
        assertEquals(2, onDisk.getIntOr("version", 0), "the disk keeps the last photo");
    }

    private static CompoundTag photo(int version) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", version);
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        return tag;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
