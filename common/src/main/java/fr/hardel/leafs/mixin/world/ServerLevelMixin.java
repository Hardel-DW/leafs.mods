package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Router;
import fr.hardel.leafs.world.ChunkBlockEvents;
import fr.hardel.leafs.world.ChunkScheduledTicks;
import fr.hardel.leafs.world.LevelBlockUpdates;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Mutable
    @Shadow
    @Final
    private LevelTicks<Block> blockTicks;

    @Mutable
    @Shadow
    @Final
    private LevelTicks<Fluid> fluidTicks;

    @Unique
    private final AtomicLong leafs$blockEventSequence = new AtomicLong();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$chunkKeyedTicks(CallbackInfo callbackInfo) {
        Router owners = LevelChunks.of(self()).owners();
        this.blockTicks = new ChunkScheduledTicks<>(self(), self()::getGameTime, RegionWorldData::blockTicks, owners);
        this.fluidTicks = new ChunkScheduledTicks<>(self(), self()::getGameTime, RegionWorldData::fluidTicks, owners);
    }

    public long getGameTime() {
        RegionWorldData ticking = WorldTickContext.activeFor(self());
        return ticking == null ? self().getLevelData().getGameTime() : ticking.time().currentTick();
    }

    @Inject(method = "tickRateManager", at = @At("HEAD"), cancellable = true)
    private void leafs$regionTickRate(CallbackInfoReturnable<TickRateManager> callbackInfo) {
        RegionWorldData ticking = WorldTickContext.activeFor(self());
        if (ticking != null) {
            callbackInfo.setReturnValue(ticking.time().rate());
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void leafs$forgetTheChunksOfTheTick(LevelChunk levelChunk, CallbackInfo callbackInfo) {
        WorldTickContext tick = WorldTickContext.current();
        if (tick != null) {
            tick.forgetChunks();
        }
    }

    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void leafs$blockEventOnTheChunk(BlockPos pos, Block block, int b0, int b1, CallbackInfo callbackInfo) {
        if (ChunkBlockEvents.post(self(), new BlockEventData(pos.immutable(), block, b0, b1), leafs$blockEventSequence.getAndIncrement())) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "clearBlockEvents", at = @At("HEAD"))
    private void leafs$clearChunkBlockEvents(BoundingBox area, CallbackInfo callbackInfo) {
        ChunkBlockEvents.clearArea(self(), area);
    }

    @Inject(method = "isHandlingTick", at = @At("HEAD"), cancellable = true)
    private void leafs$handlingTickInRegionContext(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (WorldTickContext.activeFor(self()) != null) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "getPathTypeCache", at = @At("HEAD"), cancellable = true)
    private void leafs$regionPathTypeCache(CallbackInfoReturnable<PathTypeCache> callbackInfo) {
        RegionWorldData data = WorldTickContext.activeFor(self());
        if (data != null) {
            callbackInfo.setReturnValue(data.pathTypeCache());
        }
    }

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void leafs$positionKeyedBlockUpdate(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo callbackInfo) {
        LevelBlockUpdates.onBlockUpdated(self(), pos, old, current);
        callbackInfo.cancel();
    }

    @Unique
    private ServerLevel self() {
        return (ServerLevel) (Object) this;
    }
}
