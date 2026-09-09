package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Level-wide work with a position that may move, a raid centre for one, run by the owner of that position's chunk while it ticks. */
public record AnchoredTicker(Supplier<BlockPos> pos, Runnable body, BooleanSupplier finished) {

    public long chunkKey() {
        return ChunkPos.pack(pos.get());
    }
}
