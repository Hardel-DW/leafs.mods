package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredWork;
import fr.hardel.leafs.ticking.TickingBinding;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.ChunkBlockEvents;
import fr.hardel.leafs.world.ChunkTickAccess;
import fr.hardel.leafs.world.ChunkTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The chunk carries its tickers and its block events; a thread that does not own it reads block entities without creating one, and writes blocks through its owner. */
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

    /** A block write is the owner's: a foreign thread mails it and answers with the state it can read, the write lands within a tick. */
    @WrapMethod(method = "setBlockState")
    private BlockState leafs$writeOnTheOwner(BlockPos pos, BlockState state, int flags, Operation<BlockState> original) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) {
            return original.call(pos, state, flags);
        }

        ChunkPos chunk = self.getPos();
        ChunkScheduling scheduling = RegionChunkAccess.scheduling(level.getChunkSource().chunkMap);
        if (scheduling.isOwner(chunk.x(), chunk.z())) {
            return original.call(pos, state, flags);
        }

        BlockState published = self.getBlockState(pos);
        DeferredWork.owner(DeferReason.BLOCK_WRITE, TickingManager.of(level.getServer()).metrics().deferStats(), chunk.x(), chunk.z(), () -> original.call(pos, state, flags))
            .submit(TickingBinding.of(level));
        return published == state ? null : published;
    }
}
