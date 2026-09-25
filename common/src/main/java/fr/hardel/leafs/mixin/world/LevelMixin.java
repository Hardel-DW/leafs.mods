package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.owner.DeferredWork;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.world.ChunkTickAccess;
import fr.hardel.leafs.world.RoutingNeighborUpdater;
import fr.hardel.leafs.world.RoutingRandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Final;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Mutable
    @Shadow
    @Final
    protected RandomSource random;

    @Mutable
    @Shadow
    @Final
    public CollectingNeighborUpdater neighborUpdater;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$routeUnitState(CallbackInfo callbackInfo) {
        if ((Object) this instanceof ServerLevel level) {
            this.random = new RoutingRandomSource(level);
            this.neighborUpdater = new RoutingNeighborUpdater(level, () -> new CollectingNeighborUpdater(level, level.getServer().getMaxChainedNeighborUpdates()), () -> LevelChunks.of(level).owners());
        }
    }

    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean leafs$writeOnTheOwner(BlockPos pos, BlockState state, int flags, int updateLimit, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerLevel level) || !level.isInValidBounds(pos)) {
            return original.call(pos, state, flags, updateLimit);
        }

        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        RegionBorrow.atContact(LevelRegions.of(level), chunkX, chunkZ);
        if (LevelChunks.of(level).owners().holds(chunkX, chunkZ)) {
            return original.call(pos, state, flags, updateLimit);
        }

        BlockPos target = pos.immutable();
        MutableBoolean placed = new MutableBoolean(true);
        DeferredWork.owner(level, DeferReason.BLOCK_WRITE, chunkX, chunkZ, () -> placed.setValue(original.call(target, state, flags, updateLimit))).submit();
        return placed.booleanValue();
    }

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("HEAD"), cancellable = true)
    private void leafs$regionBlockEntityPath(BlockPos pos, CallbackInfoReturnable<BlockEntity> callbackInfo) {
        if (!((Object) this instanceof ServerLevel level) || !level.isInValidBounds(pos)) {
            return;
        }

        LevelChunk chunk = level.getChunkAt(pos);
        boolean owner = LevelChunks.of(level).owners().holds(chunk.getPos().x(), chunk.getPos().z());
        callbackInfo.setReturnValue(owner ? chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) : chunk.getBlockEntities().get(pos));
    }

    @Inject(method = "addBlockEntityTicker", at = @At("HEAD"), cancellable = true)
    private void leafs$routeBlockEntityTicker(TickingBlockEntity ticker, CallbackInfo callbackInfo) {
        if (!(this instanceof ServerLevelRegionAccess)) {
            return;
        }

        BlockPos pos = ticker.getPos();
        LevelChunk chunk = RegionChunkAccess.levelChunkOrNull(((ServerLevel) (Object) this).getChunkSource().chunkMap, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) {
            return;
        }

        ((ChunkTickAccess) chunk).leafs$tickers().add(ticker);
        callbackInfo.cancel();
    }
}
