package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/** Sand placed above air falls during the block ticks of its region: the action runs there, on the region thread, in the middle of a tick. */
final class FallingSand {

    private FallingSand() {
    }

    static void whenItFalls(GameTestHelper helper, BlockPos sand, Consumer<FallingBlockEntity> action) {
        BlockPos start = helper.absolutePos(sand);
        ServerEntityEvents.ENTITY_LOAD.register((entity, _) -> {
            if (entity instanceof FallingBlockEntity falling && falling.getStartPos().equals(start)) {
                action.accept(falling);
            }
        });

        helper.setBlock(sand, Blocks.SAND);
    }
}
