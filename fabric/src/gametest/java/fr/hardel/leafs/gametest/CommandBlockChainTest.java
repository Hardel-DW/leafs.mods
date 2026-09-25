package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

public final class CommandBlockChainTest {

    /** 2026-09-23: a region sent each command to the server thread and answered false at once, so the chain stopped after its first link. */
    @GameTest(maxTicks = 12000)
    public void aPoweredChainRunsEveryLink(GameTestHelper helper) {
        BlockPos impulse = new BlockPos(0, 1, 0);
        place(helper, impulse, Blocks.COMMAND_BLOCK, false);
        for (int x = 1; x <= 3; x++) {
            place(helper, new BlockPos(x, 1, 0), Blocks.CHAIN_COMMAND_BLOCK, true);
        }

        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.startSequence().thenWaitUntil(RegionTicks.after(helper, impulse, 5)).thenExecute(() -> {
            for (int x = 0; x <= 3; x++) {
                helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(x, 3, 0));
            }
        }).thenSucceed();
    }

    private static void place(GameTestHelper helper, BlockPos pos, Block block, boolean alwaysActive) {
        helper.setBlock(pos, block.defaultBlockState().setValue(CommandBlock.FACING, Direction.EAST));
        CommandBlockEntity commandBlock = helper.getBlockEntity(pos, CommandBlockEntity.class);
        commandBlock.getCommandBlock().setCommand("setblock ~ ~2 ~ minecraft:gold_block");
        commandBlock.setAutomatic(alwaysActive);
    }
}
