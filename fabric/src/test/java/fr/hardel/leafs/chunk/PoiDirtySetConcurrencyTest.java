package fr.hardel.leafs.chunk;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.fabric.FabricRegistryFreeze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({MinecraftBootstrap.class, FabricRegistryFreeze.class})
class PoiDirtySetConcurrencyTest {
    private static final int CHUNKS = 64;
    private static final int PASSES = 400;

    /** 2026-09-04: eight regions restarted on Index -1 in the dirty set, a save flushing while a POI write marked. */
    @Test
    void markingAndFlushingFromTwoThreadsKeepsTheDirtySetSound() throws IOException, InterruptedException {
        PoiManager poiManager = poiManager(Files.createTempDirectory("leafs-poi"));
        Holder<PoiType> armorer = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.ARMORER);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread west = owner(poiManager, armorer, 0, failure);
        Thread east = owner(poiManager, armorer, CHUNKS, failure);
        west.join(60_000);
        east.join(60_000);
        assertFalse(west.isAlive(), "the west owner is stuck in a corrupted dirty set");
        assertFalse(east.isAlive(), "the east owner is stuck in a corrupted dirty set");
        if (failure.get() != null) {
            throw new AssertionError("a thread crashed", failure.get());
        }

        poiManager.close();
    }

    private static PoiManager poiManager(Path directory) throws IOException {
        return new PoiManager(
            new RegionStorageInfo("test", Level.OVERWORLD, "poi"),
            directory,
            DataFixers.getDataFixer(),
            false,
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
            new ChunkIOErrorReporter() {
                @Override
                public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo storageInfo, ChunkPos pos) {
                    throw new IllegalStateException(throwable);
                }

                @Override
                public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo storageInfo, ChunkPos pos) {
                    throw new IllegalStateException(throwable);
                }
            },
            LevelHeightAccessor.create(-64, 384)
        );
    }

    /** 2026-09-06: a region searching the exit portal read "no POI" instead of the file, and built a new portal next to the old one. Two readers of one stored section both get it, vanilla lets the second through before the first unpacked and throws. */
    @Test
    void twoThreadsReadingAStoredSectionBothGetIt() throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("leafs-poi-read");
        Holder<PoiType> armorer = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.ARMORER);
        PoiManager writer = poiManager(directory);
        for (int chunk = 0; chunk < CHUNKS; chunk++) {
            writer.add(new BlockPos(chunk << 4, 64, 0), armorer);
            writer.flush(new ChunkPos(chunk, 0));
        }

        // A read behind the writes: the disk thread takes its tasks in order, so the close that follows finds every write queued instead of dropping the last.
        writer.exists(new BlockPos(CHUNKS << 4, 64, 0), type -> true);
        writer.close();
        PoiManager reader = poiManager(directory);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> readers = List.of(reader(reader, failure), reader(reader, failure));
        for (Thread thread : readers) {
            thread.join(60_000);
            assertFalse(thread.isAlive(), "a reader is stuck loading a section");
        }

        if (failure.get() != null) {
            throw new AssertionError("a reader failed", failure.get());
        }

        reader.close();
    }

    private static Thread reader(PoiManager poiManager, AtomicReference<Throwable> failure) {
        return Thread.ofPlatform().uncaughtExceptionHandler((_, e) -> failure.set(e)).start(() -> {
            for (int chunk = 0; chunk < CHUNKS; chunk++) {
                BlockPos pos = new BlockPos(chunk << 4, 64, 0);
                if (!poiManager.exists(pos, type -> type.is(PoiTypes.ARMORER))) {
                    throw new AssertionError("the stored armorer at " + pos + " was not read");
                }
            }
        });
    }

    private static Thread owner(PoiManager poiManager, Holder<PoiType> type, int firstChunk, AtomicReference<Throwable> failure) {

        return Thread.ofPlatform().name("test-owner-" + firstChunk).uncaughtExceptionHandler((_, e) -> failure.set(e)).start(() -> {
            for (int pass = 0; pass < PASSES; pass++) {
                for (int chunk = firstChunk; chunk < firstChunk + CHUNKS; chunk++) {
                    BlockPos pos = new BlockPos(chunk << 4 | pass & 15, 64, 0);
                    poiManager.add(pos, type);
                    poiManager.remove(pos);
                    poiManager.flush(new ChunkPos(chunk, 0));
                }
            }
        });
    }
}
