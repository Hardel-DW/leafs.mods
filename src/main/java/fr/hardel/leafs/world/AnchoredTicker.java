package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.function.BooleanSupplier;

/** Level-wide work with a position, ticked as a block entity of that position: the owner of the chunk runs it. */
public record AnchoredTicker(BlockPos pos, String type, Runnable body, BooleanSupplier finished) implements TickingBlockEntity {

    @Override
    public void tick() {
        body.run();
    }

    @Override
    public boolean isRemoved() {
        return finished.getAsBoolean();
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public String getType() {
        return type;
    }
}
