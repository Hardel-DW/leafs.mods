package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ChunkTickAccess {

    ChunkTickers leafs$tickers();

    ChunkBlockEvents leafs$blockEvents();

    BlockEntity leafs$existingBlockEntity(BlockPos pos);
}
