package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.PendingUnloadClaims;
import fr.hardel.leafs.chunk.PlayerLoaderAccess;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.chunk.core.ChunkWorkers;
import fr.hardel.leafs.chunk.core.ConcurrentChunkTable;
import fr.hardel.leafs.chunk.core.ParallelChunkTaskDispatcher;
import fr.hardel.leafs.chunk.loader.PlayerChunkLoader;
import fr.hardel.leafs.chunk.loader.StageTickets;
import fr.hardel.leafs.entity.ConcurrentLongSet;
import fr.hardel.leafs.entity.ConcurrentOrderedLongSet;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.ServerLevelWorldAccess;
import fr.hardel.leafs.world.WorldTickContext;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Hook only - the chunk core wires here: the concurrent table, the pool-backed dispatcher, the
 * scheduling layer and the per-player loader are built at construction, and every routed effect
 * delegates to chunk/core. Holder creation feeds the regionizer from the drain threads, the unload
 * claims feed it from the serial decision, strictly alternating per position as the regionizer requires.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements PlayerLoaderAccess {

    @Mutable
    @Shadow
    @Final
    private LongSet chunksToEagerlySave;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Mutable
    @Shadow
    @Final
    private Long2LongMap nextChunkSaveTime;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Mutable
    @Shadow
    @Final
    private LongSet toDrop;

    @Mutable
    @Shadow
    @Final
    private Long2ByteMap chunkTypeCache;

    @Shadow
    @Final
    private BlockableEventLoop<Runnable> mainThreadExecutor;

    @Shadow
    private void runGenerationTask(ChunkGenerationTask task) {
        throw new IllegalStateException("Shadowed method body");
    }

    @Shadow
    private void scheduleUnload(long pos, ChunkHolder chunkHolder) {
        throw new IllegalStateException("Shadowed method body");
    }

    @Shadow
    abstract void onFullChunkStatusChange(ChunkPos pos, FullChunkStatus status);

    @Shadow
    private void onChunkReadyToSend(ChunkHolder chunkHolder, LevelChunk chunk) {
        throw new IllegalStateException("Shadowed method body");
    }

    @Shadow
    @Final
    private TicketStorage ticketStorage;

    @Unique
    private final ConcurrentLinkedQueue<ChunkGenerationTask> leafs$pendingGenerationTasks = new ConcurrentLinkedQueue<>();

    @Unique
    private ChunkScheduling leafs$scheduling;

    @Unique
    private PlayerChunkLoader leafs$playerLoader;

    @Override
    public PlayerChunkLoader leafs$playerLoader() {
        return leafs$playerLoader;
    }

    /**
     * Every region marks its chunks unsaved concurrently with the serial phase (light, pump); the
     * vanilla linked hash set corrupts under two writers (the 150-bot rehash AIOOBE). The scan order
     * becomes positional instead of insertion-aged, which only reorders the 20-per-tick eager-save budget.
     * The unload claims and save clocks cross threads too, now that regions tear down their own chunks.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentEagerSaves(CallbackInfo callbackInfo) {
        this.chunksToEagerlySave = new ConcurrentOrderedLongSet(32);
        this.pendingUnloads = new PendingUnloadClaims();
        this.nextChunkSaveTime = Long2LongMaps.synchronize(new Long2LongOpenHashMap());
        ConcurrentChunkTable table = new ConcurrentChunkTable();
        this.updatingChunkMap = table;
        this.visibleChunkMap = table;
        this.toDrop = new ConcurrentLongSet();
        this.chunkTypeCache = Long2ByteMaps.synchronize(new Long2ByteOpenHashMap());
        ChunkMap self = (ChunkMap) (Object) this;
        this.leafs$scheduling = new ChunkScheduling(self, self.getDistanceManager(), leafs$regions(),
            ((LeafsServerAccess) self.level.getServer()).leafs$ticking(), this.mainThreadExecutor);
        ((PropagatorAccess) self.getDistanceManager()).leafs$propagator().bindScheduling(leafs$scheduling);
        this.leafs$playerLoader = new PlayerChunkLoader(self, new StageTickets(this.ticketStorage));
    }

    /** The loader state follows the player map: creation self-heals on the first owner tick, removal is explicit. */
    @Inject(method = "updatePlayerStatus", at = @At("TAIL"))
    private void leafs$playerLoaderLifecycle(ServerPlayer player, boolean added, CallbackInfo callbackInfo) {
        if (!added) {
            leafs$playerLoader.removePlayer(player);
        }
    }

    /** The worldgen lane becomes the chunk worker pool, pumped in continuous flow; the light lane keeps its consecutive executor. */
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/server/level/ChunkTaskDispatcher", ordinal = 0))
    private ChunkTaskDispatcher leafs$parallelWorldgenDispatcher(TaskScheduler<Runnable> lane, Executor dispatcherExecutor, Operation<ChunkTaskDispatcher> original) {
        ChunkWorkers workers = leafs$chunkWorkers();
        return new ParallelChunkTaskDispatcher(TaskScheduler.wrapExecutor("leafs-worldgen", workers), dispatcherExecutor, workers.threads() + 2);
    }

    /** FEATURES and the light steps cross chunk boundaries; their exclusion covers the work, the free statuses pass through. */
    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/status/ChunkStep;apply(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkAccess> leafs$excludeCrossChunkSteps(ChunkStep step, WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, Operation<CompletableFuture<ChunkAccess>> original) {
        return leafs$scheduling.exclusion().runStep(step.targetStatus(), chunk.getPos(), () -> original.call(step, context, cache, chunk));
    }

    /** The ticking promotion body (postProcessGeneration, startTickingChunk) runs on the position's owner, not the pump. */
    @WrapOperation(method = "prepareTickingChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> leafs$tickingPromotionOnTheOwner(CompletableFuture<?> future, Function<?, ?> body, Executor pump, Operation<CompletableFuture<?>> original, @Local(argsOnly = true) ChunkHolder chunk) {
        ChunkPos pos = chunk.getPos();
        return original.call(future, body, leafs$scheduling.ownerExecutor(pos.x(), pos.z()));
    }

    /** Reached by the promotion body on the owner and by the light-sync continuation on the pump; the latter re-routes. */
    @Inject(method = "onChunkReadyToSend", at = @At("HEAD"), cancellable = true)
    private void leafs$readyToSendOnTheOwner(ChunkHolder chunkHolder, LevelChunk chunk, CallbackInfo callbackInfo) {
        ChunkPos pos = chunk.getPos();
        if (leafs$scheduling.isOwner(pos.x(), pos.z())) {
            return;
        }

        leafs$scheduling.runOnOwner(pos.x(), pos.z(), () -> onChunkReadyToSend(chunkHolder, chunk));
        callbackInfo.cancel();
    }

    /** Promotions arrive here already on the owner through the routed executors; demotions arrive from a drain thread. */
    @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"), cancellable = true)
    private void leafs$statusChangeOnTheOwner(ChunkPos pos, FullChunkStatus status, CallbackInfo callbackInfo) {
        if (leafs$scheduling.isOwner(pos.x(), pos.z())) {
            return;
        }

        leafs$scheduling.runOnOwner(pos.x(), pos.z(), () -> onFullChunkStatusChange(pos, status));
        callbackInfo.cancel();
    }

    /** The send dependencies mutate holders from a placement thread; the whole pass takes the scheduling area once. */
    @WrapMethod(method = "waitForLightBeforeSending")
    private void leafs$lockedSendDependencies(ChunkPos centerChunk, int chunkRadius, Operation<Void> original) {
        leafs$scheduling.mutateArea(centerChunk.x(), centerChunk.z(), chunkRadius + 1, () -> original.call(centerChunk, chunkRadius));
    }

    /**
     * The unload decisions run before vanilla's loop, which then finds toDrop empty. Each claim is
     * atomic under the position's scheduling cell, so a concurrent drain that raises the level again
     * keeps its chunk instead of losing it to a stale drop entry.
     */
    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void leafs$lockedUnloadDecisions(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        for (LongIterator iterator = this.toDrop.iterator(); iterator.hasNext(); iterator.remove()) {
            long pos = iterator.nextLong();
            ChunkHolder holder = leafs$scheduling.claimUnload(pos);
            if (holder != null) {
                leafs$regions().chunkHolderDestroyed(ChunkPos.getX(pos), ChunkPos.getZ(pos));
                scheduleUnload(pos, holder);
            }
        }
    }

    /** The read half of a chunk load ran on the pump; it runs on the chunk workers now, the pump is no longer a funnel. */
    @WrapOperation(method = "scheduleChunkLoad", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> leafs$loadReadOffThePump(CompletableFuture<?> future, Function<?, ?> body, Executor executor, Operation<CompletableFuture<?>> original) {
        return original.call(future, body, leafs$offPump(executor));
    }

    @WrapOperation(method = "scheduleChunkLoad", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;exceptionallyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> leafs$loadFailureOffThePump(CompletableFuture<?> future, Function<Throwable, ?> body, Executor executor, Operation<CompletableFuture<?>> original) {
        return original.call(future, body, leafs$offPump(executor));
    }

    @Unique
    private Executor leafs$offPump(Executor executor) {
        return executor == this.mainThreadExecutor ? leafs$chunkWorkers() : executor;
    }

    @Unique
    private ChunkWorkers leafs$chunkWorkers() {
        return ((LeafsServerAccess) ((ChunkMap) (Object) this).level.getServer()).leafs$ticking().chunkWorkers();
    }

    /** The double buffer is gone; the promotion step reduces to observing whether holders appeared or vanished. */
    @Inject(method = "promoteChunkMap", at = @At("HEAD"), cancellable = true)
    private void leafs$promotionIsDirtyConsumption(CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(((ConcurrentChunkTable) this.updatingChunkMap).consumeDirty());
    }

    /** Task creation happens under the scheduling locks; the start queue must accept them from any thread. */
    @WrapOperation(method = "scheduleGenerationTask", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean leafs$queueGenerationTask(List<ChunkGenerationTask> instance, Object task, Operation<Boolean> original) {
        return leafs$pendingGenerationTasks.add((ChunkGenerationTask) task);
    }

    /** Drained after the locks release, by whichever thread finished a drain batch; a forEach-then-clear would lose tasks. */
    @Inject(method = "runGenerationTasks", at = @At("HEAD"), cancellable = true)
    private void leafs$drainGenerationTasks(CallbackInfo callbackInfo) {
        ChunkGenerationTask task;
        while ((task = leafs$pendingGenerationTasks.poll()) != null) {
            runGenerationTask(task);
        }

        callbackInfo.cancel();
    }

    @Inject(method = "updateChunkScheduling",
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ChunkMap;modified:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
        require = 1, allow = 1)
    private void leafs$onChunkHolderCreated(long node, int level, ChunkHolder chunk, int oldLevel, CallbackInfoReturnable<ChunkHolder> callbackInfo) {
        leafs$regions().chunkHolderCreated(ChunkPos.getX(node), ChunkPos.getZ(node));
    }

    /** The #20b tracking split: the per-entity pass moved to the region bodies, the serial call keeps the player view diffs. */
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void leafs$serialTrackingHalf(CallbackInfo callbackInfo) {
        if (leafs$regions().body() == null) {
            return;
        }

        RegionEntityTracking.tickSerial((ChunkMap) (Object) this);
        callbackInfo.cancel();
    }

    /** The teardown runs on the region that owned the chunk at the unload decision; chunks nobody owned keep vanilla's serial queue. */
    @WrapOperation(method = "scheduleUnload", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> leafs$teardownOnTheOwner(CompletableFuture<?> future, Runnable body, Executor serialQueue, Operation<CompletableFuture<Void>> original, @Local(argsOnly = true, ordinal = 0) long pos) {
        Executor owner = task -> {
            if (!leafs$regions().unloads().offerToOwner(pos, ChunkPos.getX(pos), ChunkPos.getZ(pos), task)) {
                serialQueue.execute(task);
            }
        };

        return original.call(future, body, owner);
    }

    /** The autosave sweep offers each chunk to its owner, which snapshots it on its own thread through the budgeted lane. */
    @WrapOperation(method = "saveAllChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;saveChunkIfNeeded(Lnet/minecraft/server/level/ChunkHolder;J)Z"))
    private boolean leafs$autosaveOnTheOwner(ChunkMap map, ChunkHolder holder, long now, Operation<Boolean> original) {
        ChunkPos pos = holder.getPos();
        Region<RegionTickData> owner = leafs$regions().regionizer().regionAt(pos.x(), pos.z());
        if (leafs$regions().unloads().offer(owner, pos.x(), pos.z(), () -> map.saveChunkIfNeeded(holder, now))) {
            return false;
        }

        return original.call(map, holder, now);
    }

    /** View diffs run on the player's owner: the region for its own players, the serial pass only for players no region ticks. */
    @WrapMethod(method = "updateChunkTracking")
    private void leafs$viewDiffsOnTheOwner(ServerPlayer player, Operation<Void> original) {
        if (RegionContext.current() instanceof RegionContext.Region) {
            if (((ServerLevelEntityAccess) ((ChunkMap) (Object) this).level).leafs$entityLists().owns(player)) {
                original.call(player);
            }

            return;
        }

        if (!RegionNetworkTick.ownedByRegion(player.connection)) {
            original.call(player);
        }
    }

    /** A region serializes only chunks it owns; a foreign pending chunk stays pending and converges with ownership. */
    @WrapMethod(method = "getChunkToSend")
    private LevelChunk leafs$sendOnlyOwnedChunks(long pos, Operation<LevelChunk> original) {
        LevelChunk chunk = original.call(pos);
        if (chunk == null || !(RegionContext.current() instanceof RegionContext.Region)) {
            return chunk;
        }

        ServerLevel level = ((ChunkMap) (Object) this).level;
        RegionWorldData active = WorldTickContext.activeFor(level);

        return active != null && ((ServerLevelWorldAccess) level).leafs$worldRouter().atChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos)) == active ? chunk : null;
    }

    @Unique
    private LevelRegions leafs$regions() {
        return ((ServerLevelRegionAccess) ((ChunkMap) (Object) this).level).leafs$regions();
    }
}
