package fr.hardel.leafs.gametest;

import fr.hardel.leafs.ticking.LevelRegions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class PoiWriteTest {

    @GameTest(maxTicks = 100)
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

    @GameTest(maxTicks = 100)
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

                helper.assertFalse(pois.hasWork(), "the autosave writes every poi change");
            })
            .thenSucceed();
    }
}
