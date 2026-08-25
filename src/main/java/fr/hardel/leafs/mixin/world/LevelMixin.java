package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.RoutingNeighborUpdater;
import fr.hardel.leafs.world.RoutingRandomSource;
import fr.hardel.leafs.world.ServerLevelWorldAccess;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
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

/** The scheduled-tick sub-tick counter, the level random, the neighbor updater and block-entity ticker registration become unit-owned. */
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

    @Inject(method = "nextSubTickCount", at = @At("HEAD"), cancellable = true)
    private void leafs$regionSubTickCount(CallbackInfoReturnable<Long> callbackInfo) {
        if (!(this instanceof ServerLevelWorldAccess host) || host.leafs$worldData() == null) {
            return;
        }

        RegionWorldData data = WorldTickContext.activeFor(this);
        callbackInfo.setReturnValue((data == null ? host.leafs$worldData() : data).nextSubTick());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$routeUnitState(CallbackInfo callbackInfo) {
        if (this instanceof ServerLevelWorldAccess) {
            this.random = new RoutingRandomSource(this, this.random);
            this.neighborUpdater = new RoutingNeighborUpdater((Level) (Object) this, this.neighborUpdater);
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

    /** A ticker in a region-owned chunk registers with that region; strays keep the vanilla list, ticked level-serial. */
    @Inject(method = "addBlockEntityTicker", at = @At("HEAD"), cancellable = true)
    private void leafs$routeBlockEntityTicker(TickingBlockEntity ticker, CallbackInfo callbackInfo) {
        if (!(this instanceof ServerLevelWorldAccess host) || host.leafs$worldRouter() == null) {
            return;
        }

        BlockPos pos = ticker.getPos();
        long chunkKey = ChunkPos.pack(pos);
        RegionWorldData data = host.leafs$worldRouter().at(chunkKey);
        if (data == host.leafs$worldRouter().attached()) {
            return;
        }

        data.blockEntityTickers().add(ticker, chunkKey);
        callbackInfo.cancel();
    }
}
