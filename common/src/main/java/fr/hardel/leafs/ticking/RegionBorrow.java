package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.holder.ChunkWait;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
 * What a thread holds for one piece of game work, taken at first contact and kept until released. The server thread's head takes regions and the chunks no
 * region covers, and waits for them. Any other thread only reads a region, takes a chunk no region covers, and never waits: a region never borrows a region.
 */
public final class RegionBorrow {
    private static final ThreadLocal<RegionBorrow> CURRENT = new ThreadLocal<>();
    private static final long WAIT_NANOS = 50_000L;

    private final Set<Region<RegionTickData>> held = new LinkedHashSet<>();
    private final Map<LevelRegions, Long2ObjectOpenHashMap<RegionInbox>> heldChunks = new LinkedHashMap<>();
    /** What this thread asked for, positions and whole levels: a fold returns everything, and the demand is what gets taken again. */
    private final Map<LevelRegions, LongSet> contacts = new LinkedHashMap<>();
    private final Set<LevelRegions> wholeLevels = new LinkedHashSet<>();
    private boolean released;

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

    /** Reuses the borrow open on this thread, or opens one and returns everything at the end. */
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

    /** One more position in the demand; a fold met on the way returned everything, so the whole demand is taken again. */
    public void borrow(LevelRegions regions, int chunkX, int chunkZ) {
        contacts.computeIfAbsent(regions, _ -> new LongOpenHashSet()).add(ChunkPos.pack(chunkX, chunkZ));
        released = false;
        takeAt(regions, chunkX, chunkZ);
        if (released) {
            reacquire();
        }
    }

    /** Every region of the level joins the demand, now and after any fold. */
    public void borrowAll(LevelRegions regions) {
        wholeLevels.add(regions);
        reacquire();
    }

    /** The whole demand, looping until a pass neither takes nor returns anything: the feed may create a region while the pass runs, a fold returns everything and replaces some. */
    private void reacquire() {
        boolean changed;
        do {
            released = false;
            int before = held.size();
            for (LevelRegions regions : wholeLevels) {
                for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                    take(regions, region);
                }
            }

            contacts.forEach((regions, keys) -> {
                for (LongIterator key = keys.iterator(); key.hasNext(); ) {
                    long chunkKey = key.nextLong();
                    takeAt(regions, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
                }
            });
            changed = released || held.size() != before;
        } while (changed);
    }

    /** The region of the position, or the chunk itself when no region covers it; a region that dies under the wait is looked up again at the position. Off the server thread nothing waits: a region is read, a chunk another thread holds is left to it. */
    private void takeAt(LevelRegions regions, int chunkX, int chunkZ) {
        boolean head = !regions.live() || regions.level().getServer().isSameThread();
        while (true) {
            Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
            if (region == null) {
                if (tryBorrowChunk(regions, chunkX, chunkZ) || !head) {
                    return;
                }

                TickingManager.of(regions.level().getServer()).await(() -> tryBorrowChunk(regions, chunkX, chunkZ));
                return;
            }

            if (!head || take(regions, region)) {
                return;
            }
        }
    }

    /** Waits for a tick in flight. A region idle yet untakeable is owed a merge with one this thread holds: everything is returned so the regionizer folds, and the survivor is taken again. False once the region is dead. */
    private boolean take(LevelRegions regions, Region<RegionTickData> region) {
        while (region.state() != RegionState.DEAD) {
            if (held.contains(region) || region.tryMarkTicking()) {
                held.add(region);
                return true;
            }

            if (region.state() != RegionState.TICKING && !held.isEmpty()) {
                releaseAll();
            } else {
                awaitTick(regions, region);
            }
        }

        return false;
    }

    /** Waits for the tick in flight, running what this thread owns meanwhile: that tick may be waiting for a task it handed the server thread, a spawn search ends there. */
    private static void awaitTick(LevelRegions regions, Region<RegionTickData> region) {
        if (!regions.live()) {
            LockSupport.parkNanos(WAIT_NANOS);
            return;
        }

        TickingManager.of(regions.level().getServer()).await(() -> region.state() != RegionState.TICKING);
    }

    /** Whether this thread holds the region of the position, or the chunk itself when no region covers it. */
    public boolean holds(LevelRegions regions, int chunkX, int chunkZ) {
        Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
        if (region != null) {
            return held.contains(region);
        }

        Long2ObjectOpenHashMap<RegionInbox> chunks = heldChunks.get(regions);
        return chunks != null && chunks.containsKey(ChunkPos.pack(chunkX, chunkZ));
    }

    /** A chunk no region covers, taken once for the game work in flight: what lands there waits in its inbox for this thread. False when another thread holds it; before the regions run the server thread owns everything. */
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

    /** The regions go back first; a released chunk hands its leftover game work back through the owners, which may take the chunk again on this thread, hence the loop. */
    public void releaseAll() {
        released = !held.isEmpty();
        for (Region<RegionTickData> region : held) {
            region.markNotTicking();
        }

        held.clear();
        while (!heldChunks.isEmpty()) {
            Map<LevelRegions, Long2ObjectOpenHashMap<RegionInbox>> releasing = new LinkedHashMap<>(heldChunks);
            heldChunks.clear();
            releasing.forEach((regions, chunks) -> {
                ChunkOwners owners = LevelChunks.of(regions.level()).owners();
                for (Long2ObjectMap.Entry<RegionInbox> entry : chunks.long2ObjectEntrySet()) {
                    owners.release(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), entry.getValue());
                }
            });
        }
    }

    /** What this thread holds runs here while it waits: the chunk work of its regions and of its chunks, never their game work. A promotion may take a neighbour chunk on this thread meanwhile, so the walk is a snapshot. */
    public int drainInboxes() {
        int drained = 0;
        for (Region<RegionTickData> region : held) {
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
