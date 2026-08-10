package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.PendingUnloadClaims;
import fr.hardel.leafs.chunk.RegionEntityTracking;
import fr.hardel.leafs.entity.ConcurrentOrderedLongSet;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.ServerLevelWorldAccess;
import fr.hardel.leafs.world.WorldTickContext;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * Hook only - the regionizer feed lives in ticking/LevelRegions; the M11b unload gate that will join
 * this class belongs to chunk/. These two sites are the only mutations of {@code updatingChunkMap},
 * and they alternate strictly per position, which is exactly the regionizer's add/remove contract.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

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
    }

    @Inject(method = "updateChunkScheduling",
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ChunkMap;modified:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
        require = 1, allow = 1)
    private void leafs$onChunkHolderCreated(long node, int level, ChunkHolder chunk, int oldLevel, CallbackInfoReturnable<ChunkHolder> callbackInfo) {
        leafs$regions().chunkHolderCreated(ChunkPos.getX(node), ChunkPos.getZ(node));
    }

    /**
     * Bound to the {@code scheduleUnload} call, never to its head: {@code scheduleUnload} re-invokes
     * itself when the save future was replaced, which would double-fire the removal.
     */
    @Inject(method = "processUnloads",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V"),
        require = 1, allow = 1)
    private void leafs$onChunkHolderDestroyed(BooleanSupplier haveTime, CallbackInfo callbackInfo, @Local(ordinal = 0) long pos) {
        leafs$regions().chunkHolderDestroyed(ChunkPos.getX(pos), ChunkPos.getZ(pos));
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
