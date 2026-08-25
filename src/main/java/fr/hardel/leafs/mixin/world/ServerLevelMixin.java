package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.world.LevelBlockUpdates;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.RoutingNeighborUpdater;
import fr.hardel.leafs.world.RoutingRandomSource;
import fr.hardel.leafs.world.RoutingScheduledTicks;
import fr.hardel.leafs.world.ServerLevelWorldAccess;
import fr.hardel.leafs.world.WorldDataRouter;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Position-keyed tick diversion: world data routing, scheduled ticks, block events, region clocks, path cache. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ServerLevelWorldAccess {

    @Mutable
    @Shadow
    @Final
    private LevelTicks<Block> blockTicks;

    @Mutable
    @Shadow
    @Final
    private LevelTicks<Fluid> fluidTicks;

    @Unique
    private RegionWorldData leafs$worldData;

    @Unique
    private WorldDataRouter leafs$worldRouter;

    @Override
    public RegionWorldData leafs$worldData() {
        return leafs$worldData;
    }

    @Override
    public WorldDataRouter leafs$worldRouter() {
        return leafs$worldRouter;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createRegionWorldData(CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        this.leafs$worldData = new RegionWorldData(self::getGameTime, self::getGameTime, self::isPositionTickingWithEntitiesLoaded, self.blockEvents, RoutingRandomSource.unwrap(self.getRandom()), RoutingNeighborUpdater.unwrap(self.neighborUpdater), self.getChunkSource().chunkHoldersToBroadcast, self.getPathTypeCache());
        this.leafs$worldRouter = new WorldDataRouter(leafs$worldData);
        this.blockTicks = new RoutingScheduledTicks<>(self::isPositionTickingWithEntitiesLoaded, leafs$worldData.blockTicks());
        this.fluidTicks = new RoutingScheduledTicks<>(self::isPositionTickingWithEntitiesLoaded, leafs$worldData.fluidTicks());
    }

    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void leafs$routeBlockEvent(BlockPos pos, Block block, int b0, int b1, CallbackInfo callbackInfo) {
        leafs$worldRouter.at(pos).blockEvents().add(new BlockEventData(pos.immutable(), block, b0, b1));
        callbackInfo.cancel();
    }

    /** Region bodies are the level tick for their chunks: block-event consumers (pistons) must see it that way. */
    @Inject(method = "isHandlingTick", at = @At("HEAD"), cancellable = true)
    private void leafs$handlingTickInRegionContext(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (WorldTickContext.activeFor(this) != null) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "getPathTypeCache", at = @At("HEAD"), cancellable = true)
    private void leafs$regionPathTypeCache(CallbackInfoReturnable<PathTypeCache> callbackInfo) {
        RegionWorldData data = WorldTickContext.activeFor(this);
        if (data != null) {
            callbackInfo.setReturnValue(data.pathTypeCache());
        }
    }

    /** Scheduled ticks unpack against the owning unit's clock at chunk load (two clocks). */
    @WrapOperation(method = "startTickingChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getGameTime()J"))
    private long leafs$unpackAtOwnerClock(ServerLevel instance, Operation<Long> original, @Local(argsOnly = true) LevelChunk chunk) {
        RegionWorldData data = leafs$worldRouter.atChunk(chunk.getPos().x(), chunk.getPos().z());

        return data == leafs$worldData ? original.call(instance) : data.currentTick();
    }

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void leafs$positionKeyedBlockUpdate(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        LevelBlockUpdates.onBlockUpdated(self, leafs$worldRouter, ((ServerLevelEntityAccess) self).leafs$entityLists(), pos, old, current);
        callbackInfo.cancel();
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return leafs$worldRouter.at(pos).createTick(pos, type, delay, priority);
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return leafs$worldRouter.at(pos).createTick(pos, type, delay);
    }
}
