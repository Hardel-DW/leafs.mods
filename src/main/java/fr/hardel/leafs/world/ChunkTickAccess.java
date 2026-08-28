package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Implemented onto {@code LevelChunk} by mixin: what the chunk ticks beyond vanilla's own containers. */
public interface ChunkTickAccess {

    ChunkTickers leafs$tickers();

    ChunkBlockEvents leafs$blockEvents();

    /** What the map holds, never creating one: the read of a thread that does not own the chunk. */
    BlockEntity leafs$existingBlockEntity(BlockPos pos);
}
