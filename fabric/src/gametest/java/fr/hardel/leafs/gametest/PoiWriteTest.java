package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.SectionStorageAccess;
import fr.hardel.leafs.gametest.mixin.SectionStorageAccessor;
import fr.hardel.leafs.ticking.LevelRegions;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class PoiWriteTest {
    private static final int FAR = 4096;

    @GameTest(maxTicks = HangGuard.TICKS)
    public void aBellAddsThenDropsItsMeetingPoint(GameTestHelper helper) {
        BlockPos bell = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(bell);
        PoiManager pois = helper.getLevel().getPoiManager();

        helper.startSequence()
            .thenExecute(() -> helper.setBlock(bell, Blocks.BELL))
            .thenWaitUntil(() -> helper.assertTrue(pois.existsAtPosition(PoiTypes.MEETING, absolute), "the bell registers its meeting point"))
            .thenExecute(() -> helper.setBlock(bell, Blocks.AIR))
            .thenWaitUntil(() -> helper.assertFalse(pois.existsAtPosition(PoiTypes.MEETING, absolute), "the removed bell drops its meeting point"))
            .thenSucceed();
    }

    @GameTest(maxTicks = HangGuard.TICKS)
    public void theAutosaveWritesTheClaimOfASavedChunk(GameTestHelper helper) {
        BlockPos bell = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(bell);
        ChunkPos chunk = ChunkPos.containing(absolute);
        ServerLevel level = helper.getLevel();
        LevelRegions regions = LevelRegions.of(level);
        PoiManager pois = level.getPoiManager();

        helper.startSequence()
            .thenExecute(() -> helper.setBlock(bell, Blocks.BELL))
            .thenWaitUntil(() -> helper.assertTrue(pois.existsAtPosition(PoiTypes.MEETING, absolute), "the bell registers its meeting point"))
            .thenExecute(() -> {
                level.getChunkSource().save(true);
                helper.assertTrue(pois.take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(absolute), absolute, 1).isPresent(), "the bell is claimed");
            })
            .thenWaitUntil(() -> {
                if (regions.worldDataAt(chunk.x(), chunk.z()).savedEpoch() == regions.autosaveEpoch()) {
                    level.getChunkSource().save(false);
                }

                helper.assertFalse(dirty(pois, chunk), "the autosave writes the poi change of %s".formatted(chunk));
            })
            .thenSucceed();
    }

    /** The server thread waits for the write: a write that needs the server thread hangs the test. */
    @GameTest
    public void aGenerationThreadWritesThePoiOfAChunkNoRegionOwns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bell = helper.absolutePos(new BlockPos(1, 1, 1)).offset(FAR, 0, FAR);
        PoiManager pois = level.getPoiManager();
        LevelChunks.of(level).pool().execute(() -> level.updatePOIOnBlockStateChange(bell, Blocks.AIR.defaultBlockState(), Blocks.BELL.defaultBlockState()));
        while (!pois.existsAtPosition(PoiTypes.MEETING, bell)) {
            Thread.onSpinWait();
        }

        helper.succeed();
    }

    // The regions write the dirty set under the lock of the storage.
    private static boolean dirty(PoiManager pois, ChunkPos chunk) {
        LongSet dirty = ((SectionStorageAccessor) pois).leafs$dirtyChunks();
        return ((SectionStorageAccess) pois).leafs$lock().callLocked(() -> dirty.contains(chunk.pack()) ? 1 : 0) == 1;
    }
}
