package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.chunk.RegionChunkAccess;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The level random, the neighbor updater and block-entity ticker registration become unit-owned. */
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
            this.random = new RoutingRandomSource(level, this.random);
            this.neighborUpdater = new RoutingNeighborUpdater(level, this.neighborUpdater);
        }
    }

    /** Vanilla answers null off its one game thread; the chunk contract already decides what any thread may read, so the read follows it. */
    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("HEAD"), cancellable = true)
    private void leafs$regionBlockEntityPath(BlockPos pos, CallbackInfoReturnable<BlockEntity> callbackInfo) {
        Level self = (Level) (Object) this;
        if (self.isInValidBounds(pos) && this instanceof ServerLevelRegionAccess) {
            callbackInfo.setReturnValue(self.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE));
        }
    }

    /** Every ticker, the chunk's own at load and an anchored one from outside, joins its chunk; an unloaded position keeps vanilla's serial list. */
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
