package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Router;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.world.ChunkBlockEvents;
import fr.hardel.leafs.world.ChunkScheduledTicks;
import fr.hardel.leafs.world.LevelBlockUpdates;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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

import java.util.concurrent.atomic.AtomicLong;

/** Position-keyed tick diversion: scheduled ticks and block events go to their chunk, on the owning region's clock. */
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

    /** Posting order across chunks, so a region replays vanilla's FIFO. */
    @Unique
    private final AtomicLong leafs$blockEventSequence = new AtomicLong();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$chunkKeyedTicks(CallbackInfo callbackInfo) {
        Router owners = LevelChunks.of(self()).owners();
        this.blockTicks = new ChunkScheduledTicks<>(self(), RegionWorldData::blockTicks, owners);
        this.fluidTicks = new ChunkScheduledTicks<>(self(), RegionWorldData::fluidTicks, owners);
    }

    /** A loaded chunk keeps its own events; an unloaded position keeps vanilla's level set, drained serially. */
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

    /** Region bodies are the level tick for their chunks: block-event consumers (pistons) must see it that way. */
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

    /** Scheduled ticks unpack against the owning region's clock at chunk load (two clocks). */
    @WrapOperation(method = "startTickingChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getGameTime()J"))
    private long leafs$unpackAtOwnerClock(ServerLevel instance, Operation<Long> original, @Local(argsOnly = true) LevelChunk chunk) {
        return LevelRegions.of(instance).timeAt(chunk.getPos().x(), chunk.getPos().z(), original.call(instance));
    }

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void leafs$positionKeyedBlockUpdate(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo callbackInfo) {
        LevelBlockUpdates.onBlockUpdated(self(), pos, old, current);
        callbackInfo.cancel();
    }

    /** The fight is an anchor, the owner of its origin chunk ticks it; the level tick does not. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/end/EnderDragonFight;tick()V"))
    private void leafs$dragonFightTicksAsAnchored(EnderDragonFight fight, Operation<Void> original) {
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        RegionWorldData owner = leafs$ownerOf(pos);
        return owner == null ? new ScheduledTick<>(type, pos, self().getGameTime() + delay, priority, self().nextSubTickCount()) : owner.createTick(pos, type, delay, priority);
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        RegionWorldData owner = leafs$ownerOf(pos);
        return owner == null ? new ScheduledTick<>(type, pos, self().getGameTime() + delay, self().nextSubTickCount()) : owner.createTick(pos, type, delay);
    }

    @Unique
    private RegionWorldData leafs$ownerOf(BlockPos pos) {
        return LevelRegions.of(self()).worldDataAt(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Unique
    private ServerLevel self() {
        return (ServerLevel) (Object) this;
    }
}
