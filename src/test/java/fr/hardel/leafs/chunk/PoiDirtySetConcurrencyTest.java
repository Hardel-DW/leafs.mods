package fr.hardel.leafs.chunk;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PoiDirtySetConcurrencyTest {
    private static final int CHUNKS = 64;
    private static final int PASSES = 400;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MappedRegistry<PoiType> poiTypes = (MappedRegistry<PoiType>) BuiltInRegistries.POINT_OF_INTEREST_TYPE;
        poiTypes.bindAllTagsToEmpty();
        poiTypes.freeze();
    }

    /** 2026-09-04: eight regions restarted on Index -1 in the dirty set, a save flushing while a POI write marked. */
    @Test
    void markingAndFlushingFromTwoThreadsKeepsTheDirtySetSound() throws IOException, InterruptedException {
        PoiManager poiManager = new PoiManager(
            new RegionStorageInfo("test", Level.OVERWORLD, "poi"),
            Files.createTempDirectory("leafs-poi"),
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
