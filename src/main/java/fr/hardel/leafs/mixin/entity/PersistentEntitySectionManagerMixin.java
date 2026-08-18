package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.entity.EntityManagerAccess;
import fr.hardel.leafs.entity.EntitySectionVisibilityAccess;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Hook only - the routing lives in entity/RegionEntityPersistence. Every region transitions its own
 * chunks through these maps, so they all go concurrent; arrival, unload and autosave leave the
 * global thread for the owner of the position. saveAll keeps its serial body, it only runs under
 * the level exclusion or with the pool stopped, where the caller is a universal owner.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> implements EntityManagerAccess {

    @Mutable
    @Shadow
    @Final
    private Set<UUID> knownUuids;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Visibility> chunkVisibility;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses;

    @Mutable
    @Shadow
    @Final
    private LongSet chunksToUnload;

    @Shadow
    @Final
    private EntitySectionStorage<T> sectionStorage;

    @Shadow
    public abstract void addLegacyChunkEntities(Stream<T> entities);

    @Shadow
    private boolean processChunkUnload(long chunkKey) {
        throw new IllegalStateException("Shadowed method body");
    }

    @Shadow
    private boolean storeChunkSections(long chunkKey, Consumer<T> savedEntityVisitor) {
        throw new IllegalStateException("Shadowed method body");
    }

    @Shadow
    private LongSet getAllChunksToSave() {
        throw new IllegalStateException("Shadowed method body");
    }

    @Unique
    private RegionEntityPersistence leafs$persistence;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacades(CallbackInfo callbackInfo) {
        this.knownUuids = ConcurrentHashMap.newKeySet();
        Long2ObjectMap<Visibility> visibility = new ConcurrentLong2ObjectMap<>();
        visibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkVisibility = visibility;
        ((EntitySectionVisibilityAccess) this.sectionStorage).leafs$bindInitialVisibility(visibility);
        Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> statuses = new ConcurrentLong2ObjectMap<>();
        statuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.chunkLoadStatuses = statuses;
        this.chunksToUnload = new ConcurrentLongSet();
    }

    /** Arrival leaves the global inbox: the loaded entity chunk adds its entities on the region that owns the position. */
    @WrapOperation(method = "requestChunkLoad", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> leafs$deliverOnTheOwner(CompletableFuture<ChunkEntities<T>> future, Consumer<? super ChunkEntities<T>> inboxAdd, Operation<CompletableFuture<Void>> original) {
        if (leafs$persistence == null) {
            return original.call(future, inboxAdd);
        }

        Consumer<ChunkEntities<T>> delivery = chunk -> leafs$persistence.deliver(chunk.getPos(), () -> {
            addLegacyChunkEntities(chunk.getEntities());
            chunkLoadStatuses.put(chunk.getPos().pack(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
        });

        return original.call(future, delivery);
    }

    /** The serial call keeps only the dispatch; the save-unload of each hidden chunk runs on its owner. */
    @Inject(method = "processUnloads", at = @At("HEAD"), cancellable = true)
    private void leafs$unloadsOnTheOwner(CallbackInfo callbackInfo) {
        if (leafs$persistence != null) {
            leafs$persistence.sweepUnloads();
            callbackInfo.cancel();
        }
    }

    @Inject(method = "autoSave", at = @At("HEAD"), cancellable = true)
    private void leafs$autosaveOnTheOwner(CallbackInfo callbackInfo) {
        if (leafs$persistence != null) {
            leafs$persistence.autoSave();
            callbackInfo.cancel();
        }
    }

    /** The vanilla call drained the inbox; the routed deliveries need the universal owner to run them in line. */
    @WrapOperation(method = "saveAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;processPendingLoads()V"))
    private void leafs$drainDeliveriesWhileSavingAll(PersistentEntitySectionManager<?> manager, Operation<Void> original) {
        if (leafs$persistence == null) {
            original.call(manager);
            return;
        }

        leafs$persistence.drainPendingLoadsInline();
    }

    @Override
    public void leafs$bindPersistence(RegionEntityPersistence persistence) {
        this.leafs$persistence = persistence;
    }

    @Override
    public Visibility leafs$visibility(long chunkKey) {
        return chunkVisibility.get(chunkKey);
    }

    @Override
    public boolean leafs$unloadChunk(long chunkKey) {
        return processChunkUnload(chunkKey);
    }

    @Override
    public boolean leafs$storeChunk(long chunkKey) {
        return storeChunkSections(chunkKey, entity -> { });
    }

    @Override
    public void leafs$requeueUnload(long chunkKey) {
        chunksToUnload.add(chunkKey);
    }

    @Override
    public LongSet leafs$chunksToUnload() {
        return chunksToUnload;
    }

    @Override
    public LongSet leafs$chunksToSave() {
        return getAllChunksToSave();
    }
}
