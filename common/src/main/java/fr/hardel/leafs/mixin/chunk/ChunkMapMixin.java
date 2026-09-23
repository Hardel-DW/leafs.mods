package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.chunk.DistanceManagerAccess;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.LevelChunksAccess;
import fr.hardel.leafs.chunk.disk.PendingWrite;
import fr.hardel.leafs.chunk.holder.HolderTable;
import fr.hardel.leafs.chunk.holder.PendingUnloads;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.world.WorldTickContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements LevelChunksAccess {
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
    private Long2ByteMap chunkTypeCache;

    @Shadow
    @Final
    private BlockableEventLoop<Runnable> mainThreadExecutor;

    @Shadow
    @Final
    private TicketStorage ticketStorage;

    @Shadow
    abstract void onFullChunkStatusChange(ChunkPos pos, FullChunkStatus status);

    @Shadow
    private void onChunkReadyToSend(ChunkHolder chunkHolder, LevelChunk chunk) {
        throw new IllegalStateException("Shadowed method body");
    }

    @Unique
    /** The thread that creates a generation task installs it in its holder, then starts it; no other thread drains the list. */
    private final ThreadLocal<List<ChunkGenerationTask>> leafs$tasksCreatedHere = ThreadLocal.withInitial(ArrayList::new);

    @Unique
    private LevelChunks leafs$chunks;

    @Override
    public LevelChunks leafs$chunks() {
        return leafs$chunks;
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;setServerViewDistance(I)V"))
    private void leafs$buildTheChunkSystem(CallbackInfo callbackInfo) {
        this.chunksToEagerlySave = new ConcurrentLongSet();
        this.nextChunkSaveTime = Long2LongMaps.synchronize(new Long2LongOpenHashMap());
        this.chunkTypeCache = Long2ByteMaps.synchronize(new Long2ByteOpenHashMap());
        HolderTable table = new HolderTable(leafs$regions().regionizer().sectionShift());
        this.updatingChunkMap = table;
        this.visibleChunkMap = table;
        PendingUnloads unloading = new PendingUnloads();
        this.pendingUnloads = unloading;
        ChunkMap self = (ChunkMap) (Object) this;
        leafs$chunks = new LevelChunks(self, this.ticketStorage, table, unloading, this.mainThreadExecutor);
        ((DistanceManagerAccess) self.getDistanceManager()).leafs$bind(leafs$chunks);
    }

    @WrapMethod(method = "move")
    private void leafs$moveAsOneChange(ServerPlayer player, Operation<Void> original) {
        leafs$chunks.graphs().batch(() -> original.call(player));
    }

    @WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"))
    private ObjectCollection<ChunkMap.TrackedEntity> leafs$noLevelWidePassOnMove(Int2ObjectMap<ChunkMap.TrackedEntity> instance, Operation<ObjectCollection<ChunkMap.TrackedEntity>> original) {
        return ObjectLists.emptyList();
    }

    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/status/ChunkStep;apply(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkAccess> leafs$stepOnThePool(ChunkStep step, WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, Operation<CompletableFuture<ChunkAccess>> original) {
        return leafs$chunks.steps().apply(step, cache, chunk, () -> original.call(step, context, cache, chunk));
    }

    @WrapOperation(method = "runGenerationTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTaskDispatcher;submit(Ljava/lang/Runnable;JLjava/util/function/IntSupplier;)V"))
    private void leafs$driveOnThePool(ChunkTaskDispatcher dispatcher, Runnable task, long chunkKey, IntSupplier queueLevel, Operation<Void> original) {
        leafs$chunks.steps().run(task, chunkKey);
    }

    @WrapOperation(method = {"scheduleGenerationTask", "runGenerationTasks"}, at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ChunkMap;pendingGenerationTasks:Ljava/util/List;"))
    private List<ChunkGenerationTask> leafs$tasksOfThisThread(ChunkMap self, Operation<List<ChunkGenerationTask>> original) {
        return leafs$tasksCreatedHere.get();
    }

    @WrapOperation(method = "scheduleChunkLoad", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> leafs$loadOnThePool(CompletableFuture<?> future, Function<?, ?> body, Executor executor, Operation<CompletableFuture<?>> original, @Local(argsOnly = true) ChunkPos pos) {
        return original.call(future, body, leafs$chunks.steps().loading(pos));
    }

    @WrapOperation(method = "scheduleChunkLoad", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;exceptionallyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> leafs$loadFailureOnThePool(CompletableFuture<?> future, Function<Throwable, ?> body, Executor executor, Operation<CompletableFuture<?>> original, @Local(argsOnly = true) ChunkPos pos) {
        return original.call(future, body, leafs$chunks.steps().loading(pos));
    }

    @WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<CompoundTag> leafs$photographOnThePool(Supplier<CompoundTag> photo, Executor workerMain, Operation<CompletableFuture<CompoundTag>> original, @Local(argsOnly = true) ChunkAccess chunk) {
        return leafs$chunks.writes().photograph(chunk.getPos(), photo);
    }

    @WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> leafs$bytesToTheDisk(ChunkMap self, ChunkPos pos, Supplier<CompoundTag> join, Operation<CompletableFuture<Void>> original, @Local CompletableFuture<CompoundTag> photographed) {
        return ((PendingWrite) photographed).written();
    }

    @WrapOperation(method = "prepareTickingChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> leafs$tickingPromotionOnTheOwner(CompletableFuture<?> future, Function<?, ?> body, Executor pump, Operation<CompletableFuture<?>> original, @Local(argsOnly = true) ChunkHolder chunk) {
        ChunkPos pos = chunk.getPos();
        return original.call(future, body, leafs$owners().executor(pos.x(), pos.z()));
    }

    @Inject(method = "onChunkReadyToSend", at = @At("HEAD"), cancellable = true)
    private void leafs$readyToSendOnTheOwner(ChunkHolder chunkHolder, LevelChunk chunk, CallbackInfo callbackInfo) {
        leafs$onTheOwner(chunk.getPos(), () -> onChunkReadyToSend(chunkHolder, chunk), callbackInfo);
    }

    @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"), cancellable = true)
    private void leafs$statusChangeOnTheOwner(ChunkPos pos, FullChunkStatus status, CallbackInfo callbackInfo) {
        leafs$onTheOwner(pos, () -> onFullChunkStatusChange(pos, status), callbackInfo);
    }

    @Unique
    private void leafs$onTheOwner(ChunkPos pos, Runnable body, CallbackInfo callbackInfo) {
        if (leafs$owners().holds(pos.x(), pos.z())) {
            return;
        }

        leafs$owners().submit(pos.x(), pos.z(), Work.CHUNK, body);
        callbackInfo.cancel();
    }

    @Inject(method = "processUnloads", at = @At("HEAD"), cancellable = true)
    private void leafs$noSerialUnloads(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }

    @WrapOperation(method = "scheduleUnload", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> leafs$teardownOnTheOwner(CompletableFuture<?> future, Runnable body, Executor serialQueue, Operation<CompletableFuture<Void>> original, @Local(argsOnly = true) long pos) {
        return original.call(future, body, leafs$owners().executor(ChunkPos.getX(pos), ChunkPos.getZ(pos)));
    }

    @Inject(method = "promoteChunkMap", at = @At("HEAD"), cancellable = true)
    private void leafs$noDoubleBuffer(CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(false);
    }

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void leafs$noSerialTracking(CallbackInfo callbackInfo) {
        if (leafs$regions().live()) {
            callbackInfo.cancel();
        }
    }

    @WrapMethod(method = "saveAllChunks")
    private void leafs$autosave(boolean flushStorage, Operation<Void> original) {
        LevelRegions regions = leafs$regions();
        if (!regions.live()) {
            original.call(flushStorage);
            return;
        }

        if (!flushStorage) {
            this.nextChunkSaveTime.clear();
            regions.bumpAutosaveEpoch();
            return;
        }

        RegionBorrow.lockAll(regions);
        original.call(true);
    }

    @WrapMethod(method = "updateChunkTracking")
    private void leafs$viewDiffsOnTheOwner(ServerPlayer player, Operation<Void> original) {
        ChunkPos chunk = player.chunkPosition();
        if (WorldTickContext.ownsChunk(((ChunkMap) (Object) this).level, chunk.x(), chunk.z())) {
            original.call(player);
        }
    }

    @Unique
    private ChunkOwners leafs$owners() {
        return leafs$chunks.owners();
    }

    @Unique
    private LevelRegions leafs$regions() {
        return LevelRegions.of(((ChunkMap) (Object) this).level);
    }
}
