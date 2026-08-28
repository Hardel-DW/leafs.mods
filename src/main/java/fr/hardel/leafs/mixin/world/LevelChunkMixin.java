package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.world.ChunkBlockEvents;
import fr.hardel.leafs.world.ChunkTickAccess;
import fr.hardel.leafs.world.ChunkTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The chunk carries its tickers and its block events; a thread that does not own it reads block entities without creating one. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ChunkTickAccess {

    @Unique
    private final ChunkTickers leafs$tickers = new ChunkTickers();

    @Unique
    private final ChunkBlockEvents leafs$blockEvents = new ChunkBlockEvents();

    @Override
    public BlockEntity leafs$existingBlockEntity(BlockPos pos) {
        return ((LevelChunk) (Object) this).getBlockEntities().get(pos);
    }

    @Override
    public ChunkTickers leafs$tickers() {
        return leafs$tickers;
    }

    @Override
    public ChunkBlockEvents leafs$blockEvents() {
        return leafs$blockEvents;
    }
}
