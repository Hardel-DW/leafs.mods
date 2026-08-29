package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.chunk.ChunkMailbox;
import fr.hardel.leafs.chunk.MailHold;
import fr.hardel.leafs.chunk.propagator.AreaLock;
import fr.hardel.leafs.chunk.propagator.LeafsTicketPropagator;
import fr.hardel.leafs.ticking.RegionContext;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.RegionTickData;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// Manages a level's chunks. Respect lock order, do heavy work outside locks, and route changes to the thread that owns the affected area.
public final class ChunkScheduling {
    private static final int SCHEDULING_MARGIN = ChunkLevel.MAX_LEVEL - 33 + 2;
    private static final int UNLOADED = ChunkLevel.MAX_LEVEL + 1;
    private final ChunkMap chunkMap;
    private final DistanceManager distanceManager;
    private final LevelRegions regions;
    private final BooleanSupplier halted;
    private final Executor pump;
    private final AreaLock schedulingLock = new AreaLock(LeafsTicketPropagator.SECTION_SHIFT);
    private final GenerationExclusion exclusion = new GenerationExclusion();
    private final ThreadLocal<List<DeferredOwnerTask>> deferredOwnerTasks = new ThreadLocal<>();
    private final ChunkMailbox mailbox;

    private record DeferredOwnerTask(int chunkX, int chunkZ, MailHold hold, Runnable task) {}

    public ChunkScheduling(ChunkMap chunkMap, DistanceManager distanceManager, LevelRegions regions, BooleanSupplier halted, Executor pump, ChunkMailbox mailbox) {
        this.chunkMap = chunkMap;
        this.distanceManager = distanceManager;
        this.regions = regions;
        this.halted = halted;
        this.pump = pump;
        this.mailbox = mailbox;
    }

    public ChunkMailbox mailbox() {
        return mailbox;
    }

    public GenerationExclusion exclusion() {
        return exclusion;
    }

    /** The drain reaction, called by the propagator under the drained section's ticket area: level writes first, then the promotions; their side effects stage until the locks release. */
    public void applyLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
        openStagingFrame();

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

    /** After the area locks release: a staged effect may post a ticket, whose storage monitor a writer can hold while waiting on a cell, a cycle if run under the area. */
    public void startCollectedTasks() {
        chunkMap.runGenerationTasks();
        flushStagedTasks();
    }

    private boolean openStagingFrame() {
        if (deferredOwnerTasks.get() != null) {
            return false;
        }

        deferredOwnerTasks.set(new ArrayList<>());
        return true;
    }

    private void flushStagedTasks() {
        List<DeferredOwnerTask> deferred = deferredOwnerTasks.get();
        if (deferred == null) {
            return;
        }

        deferredOwnerTasks.remove();
        for (DeferredOwnerTask task : deferred) {
            runOnOwner(task.chunkX(), task.chunkZ(), task.hold(), task.task());
        }
    }

    /** A request path (getChunkFuture, addTicketAndLoadWithRadius) locks its area, then starts what it built. */
    public <T> T requestArea(int chunkX, int chunkZ, int radius, Supplier<T> request) {
        boolean stagedHere = openStagingFrame();
        try {
            T result = locked(chunkX, chunkZ, radius + SCHEDULING_MARGIN, request);
            chunkMap.runGenerationTasks();
            return result;
        } finally {
            closeStagingFrame(stagedHere);
        }
    }

    /** The one way to ask a position for a status; null when no ticket reached it yet, complete at delivery. */
    public CompletableFuture<?> requestStatus(int chunkX, int chunkZ, ChunkStatus status) {
        ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
        if (holder == null) {
            return null;
        }

        return requestArea(chunkX, chunkZ, 0, () -> holder.scheduleChunkGenerationTask(status, chunkMap));
    }

    /** Holder mutation outside a drain, like the send dependencies a player placement adds. */
    public void mutateArea(int chunkX, int chunkZ, int radius, Runnable mutation) {
        boolean stagedHere = openStagingFrame();
        try {
            locked(chunkX, chunkZ, radius, () -> {
                mutation.run();
                return null;
            });
        } finally {
            closeStagingFrame(stagedHere);
        }
    }

    private <T> T locked(int chunkX, int chunkZ, int radius, Supplier<T> body) {
        AreaLock.Node node = schedulingLock.lock(chunkX, chunkZ, radius);
        try {
            return body.get();
        } finally {
            schedulingLock.unlock(node);
        }
    }

    /** The frame closes on failure too, or the thread keeps staging every later owner task into a list nobody runs. */
    private void closeStagingFrame(boolean stagedHere) {
        if (stagedHere) {
            flushStagedTasks();
        }
    }

    /** The serial unload decision, atomic under the position's scheduling cell: a concurrent drain that raised the level again keeps its chunk. */
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

    /** The ticking promotion reads its 3x3 (fluid post processing crosses the chunk edge): what the range future verified stays FULL until the mail ran. */
    public Executor tickingPromotionExecutor(int chunkX, int chunkZ) {
        return task -> runOnOwner(chunkX, chunkZ, MailHold.FULL_NEIGHBOURHOOD, task);
    }

    public void runOnOwner(int chunkX, int chunkZ, Runnable task) {
        runOnOwner(chunkX, chunkZ, MailHold.CHUNK, task);
    }

    /** Inline on the owner, mailed to the chunk otherwise; before activation the pump plays vanilla's main thread. */
    private void runOnOwner(int chunkX, int chunkZ, MailHold hold, Runnable task) {
        List<DeferredOwnerTask> deferred = deferredOwnerTasks.get();
        if (deferred != null) {
            deferred.add(new DeferredOwnerTask(chunkX, chunkZ, hold, task));
            return;
        }

        if (isOwner(chunkX, chunkZ)) {
            task.run();
            return;
        }

        if (regions.body() == null) {
            pump.execute(task);
            return;
        }

        mailbox.post(chunkX, chunkZ, hold, task);
        if (regions.regionizer().regionAt(chunkX, chunkZ) == null) {
            mailbox.drainOnWorkers(ChunkPos.pack(chunkX, chunkZ));
        }
    }

    /** The owning region on its worker, the thread holding the chunk's claim or borrowing its region, or a universal owner. */
    public boolean isOwner(int chunkX, int chunkZ) {
        return currentRegionOwns(chunkX, chunkZ) || mailbox.claimedByCurrentThread(ChunkPos.pack(chunkX, chunkZ)) || borrowHoldsRegion(chunkX, chunkZ) || isUniversalOwner();
    }

    private boolean borrowHoldsRegion(int chunkX, int chunkZ) {
        RegionBorrow borrow = RegionBorrow.current();
        return borrow != null && borrow.holds(regions, chunkX, chunkZ);
    }

    // Universal ownership is the absence of rivals: a level whose regions have not started, or a halted pool.
    public boolean isUniversalOwner() {
        return (regions.body() == null || halted.getAsBoolean()) && chunkMap.level.getServer().isSameThread();
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
