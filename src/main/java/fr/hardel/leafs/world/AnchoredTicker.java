package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.BooleanSupplier;

/** Level-wide work with a position, run by the owner of that position's chunk while it ticks. */
public record AnchoredTicker(BlockPos pos, Runnable body, BooleanSupplier finished) {

    public long chunkKey() {
        return ChunkPos.pack(pos);
    }
}

