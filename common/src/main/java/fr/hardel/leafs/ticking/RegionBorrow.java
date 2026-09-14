package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.holder.ChunkWait;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/**
 * The locks a thread holds, taken at first contact and kept until it releases them all. The server thread locks the regions it touches and the chunks
 * no region covers, and waits for them, until its tick or its task ends. Any other thread reads regions without locking, takes a chunk no region covers,
 * and never waits: a region never locks a region.
 */
public final class RegionBorrow {
    private static final ThreadLocal<RegionBorrow> CURRENT = new ThreadLocal<>();
    private static final long WAIT_NANOS = 50_000L;

    private final Set<Region<RegionTickData>> held = new LinkedHashSet<>();
    private final Map<LevelRegions, Long2ObjectOpenHashMap<RegionInbox>> heldChunks = new LinkedHashMap<>();

    private RegionBorrow() {
    }

    public static RegionBorrow enter() {
        RegionBorrow borrow = new RegionBorrow();
        CURRENT.set(borrow);
        ChunkWait.enterScope();
        return borrow;
    }

    public static void exit() {
        ChunkWait.exitScope();
        CURRENT.remove();
    }

    /** Reuses the locks already open on this thread, or opens a set and releases everything at the end: what runs inside a tick or a task shares its locks. */
    public static <T> T hold(Function<RegionBorrow, T> body) {
        RegionBorrow current = CURRENT.get();
        if (current != null) {
            return body.apply(current);
        }

        RegionBorrow borrow = enter();
        try {
            return body.apply(borrow);
        } finally {
            borrow.releaseAll();
            exit();
        }
    }

    /** Null on every thread that is not borrowing. */
    public static RegionBorrow current() {
        return CURRENT.get();
    }

    /** A borrowing thread meets an entity: it takes the region of the entity's position, as it does for a chunk it reads or writes. Nothing happens without a borrow. */
    public static void atContact(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            ChunkPos chunk = entity.chunkPosition();
            atContact(LevelRegions.of(level), chunk.x(), chunk.z());
        }
    }

    public static void atContact(LevelRegions regions, int chunkX, int chunkZ) {
        RegionBorrow borrow = CURRENT.get();
        if (borrow != null) {
            borrow.borrow(regions, chunkX, chunkZ);
        }
    }

    /** Locks the region of the position, or the chunk itself when no region covers it; a region that dies under the wait is looked up again at the position. Off the server thread nothing waits: a region is read, a chunk another thread holds is left to it. */
    public void borrow(LevelRegions regions, int chunkX, int chunkZ) {
        boolean serverThread = serverThread(regions);
        while (true) {
            Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
            if (region == null) {
                if (tryBorrowChunk(regions, chunkX, chunkZ) || !serverThread) {
                    return;
                }

                TickingManager.of(regions.level().getServer()).await(() -> tryBorrowChunk(regions, chunkX, chunkZ));
                return;
            }

            if (!serverThread || take(regions, region)) {
                return;
            }
        }
    }

    /** Locks every region of the level, the idle ones first so a tick in flight finds its merge partner already locked, looping until a pass locks nothing new: the feed may create one while the pass runs. Only the server thread locks. */
    public void borrowAll(LevelRegions regions) {
        if (!serverThread(regions)) {
            return;
        }

        int before;
        do {
            before = held.size();
            for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                if (region.tryHold()) {
                    held.add(region);
                }
            }

            for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                take(regions, region);
            }
        } while (held.size() != before);
    }

    /** Before the regions run the server thread owns everything; once they run, only it locks and waits. */
    private static boolean serverThread(LevelRegions regions) {
        return !regions.live() || TickingManager.of(regions.level().getServer()).onServerThread();
    }

    /** Locks the region, waiting for a tick in flight. A pending merge does not stop the server thread: it runs at the release, as after a tick. False once the region is dead. */
    private boolean take(LevelRegions regions, Region<RegionTickData> region) {
        while (region.state() != RegionState.DEAD) {
            if (held.contains(region) || region.tryHold()) {
                held.add(region);
                return true;
            }

            awaitTick(regions, region);
        }

        return false;
    }

    /** Waits for the tick in flight, running what this thread owns meanwhile: that tick may be waiting for a task it handed the server thread, a spawn search ends there. The work run meanwhile may take the region for this very borrow, which ends the wait too. */
    private void awaitTick(LevelRegions regions, Region<RegionTickData> region) {
        if (!regions.live()) {
            LockSupport.parkNanos(WAIT_NANOS);
            return;
        }

        ThreadWaits.Wait outer = ThreadWaits.open(() -> "waiting for " + region + " in " + regions.level().dimension().identifier());
        try {
            TickingManager.of(regions.level().getServer()).await(() -> held.contains(region) || region.state() != RegionState.TICKING);
        } finally {
            ThreadWaits.close(outer);
        }
    }

    /** Whether this thread holds the chunk itself, taken before any region covered it, or the region of the position. */
    public boolean holds(LevelRegions regions, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<RegionInbox> chunks = heldChunks.get(regions);
        if (chunks != null && chunks.containsKey(ChunkPos.pack(chunkX, chunkZ))) {
            return true;
        }

        Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
        return region != null && held.contains(region);
    }

    /** A chunk no region covers, taken once until the release: what lands there waits in its inbox for this thread. False when another thread holds it; before the regions run the server thread owns everything. */
    public boolean tryBorrowChunk(LevelRegions regions, int chunkX, int chunkZ) {
        if (!regions.live()) {
            return true;
        }

        long key = ChunkPos.pack(chunkX, chunkZ);
        Long2ObjectOpenHashMap<RegionInbox> chunks = heldChunks.computeIfAbsent(regions, _ -> new Long2ObjectOpenHashMap<>());
        if (chunks.containsKey(key)) {
            return true;
        }

        RegionInbox taken = LevelChunks.of(regions.level()).owners().borrow(chunkX, chunkZ);
        if (taken == null) {
            return false;
        }

        chunks.put(key, taken);
        return true;
    }

    /** The regions go back first, then the chunks; a released chunk hands its leftover back through the owners, nothing runs on this thread. */
    public void releaseAll() {
        for (Region<RegionTickData> region : held) {
            region.markNotTicking();
        }

        held.clear();
        heldChunks.forEach((regions, chunks) -> {
            ChunkOwners owners = LevelChunks.of(regions.level()).owners();
            for (Long2ObjectMap.Entry<RegionInbox> entry : chunks.long2ObjectEntrySet()) {
                owners.release(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), entry.getValue());
            }
        });
        
        heldChunks.clear();
    }

    /** What this thread holds runs here while it waits: the chunk work of its regions and of its chunks, never their game work. A promotion may take a neighbour chunk on this thread meanwhile, so the walk is a snapshot. */
    public int drainInboxes() {
        int drained = 0;
        for (Region<RegionTickData> region : List.copyOf(held)) {
            drained += region.data().inbox().drainChunkWork();
        }

        for (Long2ObjectOpenHashMap<RegionInbox> chunks : List.copyOf(heldChunks.values())) {
            for (RegionInbox inbox : List.copyOf(chunks.values())) {
                drained += inbox.drainChunkWork();
            }
        }

        return drained;
    }

    public int size() {
        return held.size();
    }
}
