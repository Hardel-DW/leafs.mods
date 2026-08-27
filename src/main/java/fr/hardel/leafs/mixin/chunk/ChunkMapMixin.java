package fr.hardel.leafs.mixin.chunk;

import net.minecraft.server.MinecraftServer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.PendingUnloadClaims;
import fr.hardel.leafs.chunk.PlayerLoaderAccess;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.chunk.StalledShutdown;
import fr.hardel.leafs.chunk.ChunkMailbox;
import fr.hardel.leafs.chunk.ChunkTicketHolds;
import fr.hardel.leafs.chunk.ChunkUnloadAccess;
import fr.hardel.leafs.chunk.ChunkUnloads;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.chunk.core.ChunkWorkers;
import fr.hardel.leafs.chunk.core.ConcurrentChunkTable;
import fr.hardel.leafs.chunk.core.ParallelChunkTaskDispatcher;
import fr.hardel.leafs.chunk.loader.PlayerChunkLoader;
import fr.hardel.leafs.chunk.loader.StageTickets;
import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.WorldTickContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
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
import java.util.Queue;

/** Hook only, the chunk core wires here at construction. Holder creation and unload claims feed the regionizer, strictly alternating per position. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements PlayerLoaderAccess, ChunkUnloadAccess {

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

    @Shadow
    @Final
    private Queue<Runnable> unloadQueue;

    @Shadow
    @Final
    private ThreadedLevelLightEngine lightEngine;

    @Shadow
    @Final
    private PoiManager poiManager;

    @Shadow
    @Final
    private ChunkTaskDispatcher worldgenTaskDispatcher;

    @Shadow
    @Final
    private ChunkTaskDispatcher lightTaskDispatcher;

    @Unique
    private final StalledShutdown leafs$stalledShutdown = new StalledShutdown();

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
    private ChunkUnloads leafs$unloads;

    @Unique
    private ChunkScheduling leafs$scheduling;

    @Unique
    private PlayerChunkLoader leafs$playerLoader;

    @Override
    public PlayerChunkLoader leafs$playerLoader() {
        return leafs$playerLoader;
    }

    @Override
    public ChunkUnloads leafs$unloads() {
        return leafs$unloads;
    }

    @Override
    public void leafs$scheduleUnload(long pos, ChunkHolder holder) {
        scheduleUnload(pos, holder);
    }

    /** Regions, light and pump write these maps concurrently; the vanilla hash sets corrupt under two writers (150-bot rehash AIOOBE). */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentEagerSaves(CallbackInfo callbackInfo) {
        this.chunksToEagerlySave = new ConcurrentLongSet();
        this.pendingUnloads = new PendingUnloadClaims();
        this.nextChunkSaveTime = Long2LongMaps.synchronize(new Long2LongOpenHashMap());
        ConcurrentChunkTable table = new ConcurrentChunkTable();
        this.updatingChunkMap = table;
        this.visibleChunkMap = table;
        this.toDrop = new ConcurrentLongSet();
        this.chunkTypeCache = Long2ByteMaps.synchronize(new Long2ByteOpenHashMap());
        ChunkMap self = (ChunkMap) (Object) this;
        TickingManager ticking = TickingManager.of(self.level.getServer());
        this.leafs$scheduling = new ChunkScheduling(self, self.getDistanceManager(), leafs$regions(), ticking::halted, ticking.metrics().deferStats(), this.mainThreadExecutor, new ChunkMailbox(new ChunkTicketHolds(self.level)));
        ((PropagatorAccess) self.getDistanceManager()).leafs$propagator().bindScheduling(leafs$scheduling);
        this.leafs$playerLoader = new PlayerChunkLoader(self, new StageTickets(this.ticketStorage));
        this.leafs$unloads = new ChunkUnloads(self, this.toDrop, leafs$regions(), leafs$ticking().metrics().chunkUnloads());
    }

    /** The visibility pass of a move runs on the regions, against every player that moved; the rest of the move stays. */
    @WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"))
    private ObjectCollection<ChunkMap.TrackedEntity> leafs$noLevelWidePassOnMove(Int2ObjectMap<ChunkMap.TrackedEntity> instance, Operation<ObjectCollection<ChunkMap.TrackedEntity>> original) {
        return ObjectLists.emptyList();
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

    /** Only this class sees the sources vanilla's stop loop waits on, so it is the one that can name them. */
    @WrapMethod(method = "hasWork")
    private boolean leafs$nameWhatHoldsTheShutdown(Operation<Boolean> original) {
        boolean hasWork = original.call();
        ChunkMap self = (ChunkMap) (Object) this;
        if (hasWork && leafs$ticking().halted() && leafs$stalledShutdown.due()) {
            leafs$stalledShutdown
                .holding("pendingUnloads", pendingUnloads.size())
                .holding("updatingChunkMap", updatingChunkMap.size())
                .holding("toDrop", toDrop.size())
                .holding("unloadQueue", unloadQueue.size())
                .holding("light", lightEngine.hasLightWork())
                .holding("poi", poiManager.hasWork())
                .holding("worldgen", worldgenTaskDispatcher.hasWork())
                .holding("lightTasks", lightTaskDispatcher.hasWork())
                .holding("tickets", self.getDistanceManager().hasTickets())
                .holding("mail", leafs$scheduling.mailbox().size())
                .report(self.level);
        }

        return hasWork;
    }

    /** Regions decide their own unloads; vanilla's drop loop sees nothing, and the server thread decides for everyone once the pool stopped. */
    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void leafs$decideAllOnShutdown(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        if (leafs$ticking().halted()) {
            leafs$unloads.decideAll();
        }
    }

    @WrapOperation(method = "processUnloads", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;iterator()Lit/unimi/dsi/fastutil/longs/LongIterator;"))
    private LongIterator leafs$noSerialDropLoop(LongSet instance, Operation<LongIterator> original) {
        return LongIterators.EMPTY_ITERATOR;
    }

    /** Each region saves its own eager chunks; the serial pass only saves once the pool stopped. */
    @WrapOperation(method = "processUnloads", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;saveChunksEagerly(Ljava/util/function/BooleanSupplier;)V"))
    private void leafs$eagerSavesOnTheRegions(ChunkMap instance, BooleanSupplier haveTime, Operation<Void> original) {
        if (leafs$regions().body() == null || leafs$ticking().halted()) {
            original.call(instance, haveTime);
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
        return leafs$ticking().chunkWorkers();
    }

    @Unique
    private TickingManager leafs$ticking() {
        return TickingManager.of(((ChunkMap) (Object) this).level.getServer());
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
        leafs$ticking().metrics().chunkLoads().increment();
    }

    /** The per-entity pass moved to the region bodies, the serial call keeps the view diffs of the orphans. */
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void leafs$serialTrackingHalf(CallbackInfo callbackInfo) {
        if (leafs$regions().body() == null) {
            return;
        }

        ChunkMap self = (ChunkMap) (Object) this;
        RegionEntityTracking.tickSerial(self);
        leafs$ticking().markSerial(self.level, TickStages.serialTracking);
        callbackInfo.cancel();
    }

    /** A claimed chunk belongs to no region any more; its teardown after the save runs on the chunk workers, like its load did. */
    @WrapOperation(method = "scheduleUnload", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> leafs$teardownOnTheChunkWorkers(CompletableFuture<?> future, Runnable body, Executor serialQueue, Operation<CompletableFuture<Void>> original) {
        return original.call(future, body, leafs$chunkWorkers());
    }

    /**
     * The epoch bump replaces the holder walk, each region saves its own chunks. A flush waits for every live region to reach the epoch,
     * then flushes the storage. An empty server or a stopped pool keeps the vanilla walk, the server thread is then the universal owner.
     */
    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void leafs$epochAutosave(boolean flushStorage, CallbackInfo callbackInfo) {
        ChunkMap self = (ChunkMap) (Object) this;
        MinecraftServer server = self.level.getServer();
        if (server.getPlayerList().getPlayers().isEmpty() || leafs$ticking().halted()) {
            return;
        }

        this.nextChunkSaveTime.clear();
        LevelRegions regions = leafs$regions();
        regions.bumpAutosaveEpoch(flushStorage);
        callbackInfo.cancel();
        if (!flushStorage) {
            return;
        }

        RegionBorrow borrow = RegionBorrow.current();
        if (borrow != null) {
            borrow.releaseAll();
        }

        long epoch = regions.autosaveEpoch();
        server.managedBlock(() -> regions.autosaveReached(epoch, this.visibleChunkMap.values(), self.level.players()));
        self.level.getPoiManager().flushAll();
        self.synchronize(true).join();
    }

    /** View diffs run on the player's owner: the region for its own players, the serial pass only for players no region ticks. */
    @WrapMethod(method = "updateChunkTracking")
    private void leafs$viewDiffsOnTheOwner(ServerPlayer player, Operation<Void> original) {
        if (RegionContext.current() instanceof RegionContext.Region) {
            ChunkPos chunk = player.chunkPosition();
            if (WorldTickContext.ownsChunk(((ChunkMap) (Object) this).level, chunk.x(), chunk.z())) {
                original.call(player);
            }

            return;
        }

        if (!RegionNetworkTick.ownedByRegion(player)) {
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

        return WorldTickContext.ownsChunk(((ChunkMap) (Object) this).level, ChunkPos.getX(pos), ChunkPos.getZ(pos)) ? chunk : null;
    }

    @Unique
    private LevelRegions leafs$regions() {
        return LevelRegions.of(((ChunkMap) (Object) this).level);
    }
}
