package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
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
}
