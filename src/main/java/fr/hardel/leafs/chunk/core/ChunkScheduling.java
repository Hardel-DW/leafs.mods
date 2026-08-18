package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.chunk.propagator.AreaLock;
import fr.hardel.leafs.chunk.propagator.LeafsTicketPropagator;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.TickingManager;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The sharded coordination of one level's chunk system. Holder level writes and promotions run under
 * the scheduling area lock, taken after the propagator's ticket area and never before it. Everything
 * heavy stays outside: generation tasks are built under the lock and started after, and the effects
 * that touch live game state route to the thread that owns the position. A thread that holds the
 * level exclusion or the tick barrier owns every position, because no region ticks under either.
 */
public final class ChunkScheduling {
    private static final int SCHEDULING_MARGIN = ChunkLevel.MAX_LEVEL - 33 + 2;
    private static final int UNLOADED = ChunkLevel.MAX_LEVEL + 1;
    private final ChunkMap chunkMap;
    private final DistanceManager distanceManager;
    private final LevelRegions regions;
    private final TickingManager ticking;
    private final Executor pump;
    private final AreaLock schedulingLock = new AreaLock(LeafsTicketPropagator.SECTION_SHIFT);
    private final GenerationExclusion exclusion = new GenerationExclusion();
    private final ThreadLocal<List<DeferredOwnerTask>> deferredOwnerTasks = new ThreadLocal<>();

    /** An owner routing built while the thread held the drained ticket area; its hold ticket posts after the release. */
    private record DeferredOwnerTask(int chunkX, int chunkZ, Runnable task) {
    }

    public ChunkScheduling(ChunkMap chunkMap, DistanceManager distanceManager, LevelRegions regions, TickingManager ticking, Executor pump) {
        this.chunkMap = chunkMap;
        this.distanceManager = distanceManager;
        this.regions = regions;
        this.ticking = ticking;
        this.pump = pump;
    }

    public GenerationExclusion exclusion() {
        return exclusion;
    }

    /**
     * The drain reaction, called by the propagator while it holds the 3x3 ticket area of the drained
     * section. Level writes come first so every promotion sees its whole neighbourhood, then the
     * status cancellations, then the future wiring whose side effects ride the owner executors.
     */
    public void applyLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
        if (deferredOwnerTasks.get() == null) {
            deferredOwnerTasks.set(new ArrayList<>());
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Long2ByteMap.Entry entry : updates.long2ByteEntrySet()) {
            int chunkX = ChunkPos.getX(entry.getLongKey());
            int chunkZ = ChunkPos.getZ(entry.getLongKey());
            minX = Math.min(minX, chunkX);
            minZ = Math.min(minZ, chunkZ);
            maxX = Math.max(maxX, chunkX);
            maxZ = Math.max(maxZ, chunkZ);
        }

        AreaLock.Node node = schedulingLock.lock(minX - SCHEDULING_MARGIN, minZ - SCHEDULING_MARGIN, maxX + SCHEDULING_MARGIN, maxZ + SCHEDULING_MARGIN);
        try {
            List<ChunkHolder> changed = new ArrayList<>();
            for (Long2ByteMap.Entry entry : updates.long2ByteEntrySet()) {
                long pos = entry.getLongKey();
                int level = LeafsTicketPropagator.convertBetweenTicketLevels(entry.getByteValue());
                ChunkHolder holder = distanceManager.getChunk(pos);
                int holderLevel = holder == null ? UNLOADED : holder.getTicketLevel();
                if (holderLevel == level) {
                    continue;
                }

                holder = distanceManager.updateChunkScheduling(pos, level, holder, holderLevel);
                if (holder != null) {
                    changed.add(holder);
                }
            }

            for (ChunkHolder holder : changed) {
                holder.updateHighestAllowedStatus(chunkMap);
            }
            for (ChunkHolder holder : changed) {
                ChunkPos pos = holder.getPos();
                holder.updateFutures(chunkMap, ownerExecutor(pos.x(), pos.z()));
            }
        } finally {
            schedulingLock.unlock(node);
        }
    }

    /**
     * Called by the propagator after each drained section releases its locks: the generation tasks
     * start and the deferred owner routings queue for real. Posting a hold ticket takes the storage
     * monitor, which a concurrent ticket writer holds while it waits for a cell of the ticket area;
     * doing it while the drain still held that area would close a cycle between the two.
     */
    public void startCollectedTasks() {
        chunkMap.runGenerationTasks();
        List<DeferredOwnerTask> deferred = deferredOwnerTasks.get();
        if (deferred == null) {
            return;
        }

        deferredOwnerTasks.remove();
        for (DeferredOwnerTask task : deferred) {
            runOnOwner(task.chunkX(), task.chunkZ(), task.task());
        }
    }

    /** A request path (getChunkFuture, addTicketAndLoadWithRadius) locks its area, then starts what it built. */
    public <T> T requestArea(int chunkX, int chunkZ, int radius, Supplier<T> request) {
        T result;
        AreaLock.Node node = schedulingLock.lock(chunkX, chunkZ, radius + SCHEDULING_MARGIN);
        try {
            result = request.get();
        } finally {
            schedulingLock.unlock(node);
        }

        chunkMap.runGenerationTasks();
        return result;
    }

    /** The one way to ask a position for a status: no holder means no ticket reached it yet and the caller must post one first. */
    public void requestStatus(int chunkX, int chunkZ, ChunkStatus status) {
        ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        if (holder == null) {
            return;
        }

        requestArea(chunkX, chunkZ, 0, () -> holder.scheduleChunkGenerationTask(status, chunkMap));
    }

    /** Holder mutation outside a drain, like the send dependencies a player placement adds. */
    public void mutateArea(int chunkX, int chunkZ, int radius, Runnable mutation) {
        AreaLock.Node node = schedulingLock.lock(chunkX, chunkZ, radius);
        try {
            mutation.run();
        } finally {
            schedulingLock.unlock(node);
        }
    }

    /**
     * The serial unload decision, atomic against a concurrent drain that would revive the position:
     * under the position's scheduling cell the level is re-read, and a chunk whose ticket level rose
     * again stays. The claimed holder moves to the pending unloads before the cell releases.
     */
    public ChunkHolder claimUnload(long pos) {
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);
        AreaLock.Node node = schedulingLock.lock(chunkX, chunkZ, 0);
        try {
            ChunkHolder holder = distanceManager.getChunk(pos);
            if (holder == null || ChunkLevel.isLoaded(holder.getTicketLevel())) {
                return null;
            }

            chunkMap.updatingChunkMap.remove(pos);
            chunkMap.pendingUnloads.put(pos, holder);
            return holder;
        } finally {
            schedulingLock.unlock(node);
        }
    }

    public Executor ownerExecutor(int chunkX, int chunkZ) {
        return task -> runOnOwner(chunkX, chunkZ, task);
    }

    /**
     * Runs the task on the owner of the position: inline for a universal owner or the owning region's
     * own thread, queued through the region task lane otherwise. Before the level activates its
     * regions, the pump plays vanilla's main thread, which is what drives the spawn preparation.
     */
    public void runOnOwner(int chunkX, int chunkZ, Runnable task) {
        if (isOwner(chunkX, chunkZ)) {
            task.run();
            return;
        }

        List<DeferredOwnerTask> deferred = deferredOwnerTasks.get();
        if (deferred != null) {
            deferred.add(new DeferredOwnerTask(chunkX, chunkZ, task));
            return;
        }

        RegionScheduler<RegionTickData> scheduler = regions.taskScheduler();
        if (scheduler == null) {
            pump.execute(task);
            return;
        }

        scheduler.queue(chunkX, chunkZ, task);
    }

    public boolean isOwner(int chunkX, int chunkZ) {
        return isUniversalOwner() || currentRegionOwns(chunkX, chunkZ);
    }

    /**
     * Exclusion or barrier held means no region ticks anywhere on this level. The server thread is a
     * universal owner unconditionally: every legitimate vanilla sync load runs there, between level
     * ticks included. The accepted residual, documented in Fonctionnement, is that server-thread work
     * outside the exclusion can still read a chunk a region owns; the contract's new coverage is
     * every other thread.
     */
    public boolean isUniversalOwner() {
        if (regions.ownership().isLevelSerialHeldByCurrentThread() || ticking.barrier().isHeldByCurrentThread()) {
            return true;
        }

        return chunkMap.level.getServer().isSameThread();
    }

    /** The refusal counters of this level's server; the chunk contract counts here at every throw. */
    public DeferStats deferStats() {
        return ticking.metrics().deferStats();
    }

    /** Strict region ownership, dimension included: region ids repeat across dimensions and would otherwise collide. */
    public boolean currentRegionOwns(int chunkX, int chunkZ) {
        if (!(RegionContext.current() instanceof RegionContext.Region(long id, String dimension)) || !dimension.equals(regions.dimensionName())) {
            return false;
        }

        Region<RegionTickData> owner = regions.regionizer().regionAtUnsynchronised(chunkX, chunkZ);
        return owner != null && owner.id() == id;
    }
}
