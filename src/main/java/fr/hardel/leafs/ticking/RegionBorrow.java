package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/** What the server thread holds for one piece of head work, taken at first contact and kept until released: regions, and the chunks no region covers. A region never borrows. */
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
        return borrow;
    }

    public static void exit() {
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

    /** The region of the position, or the chunk itself when no region covers it; a region that dies under the wait is looked up again at the position. */
    public void borrow(LevelRegions regions, int chunkX, int chunkZ) {
        while (true) {
            Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
            if (region == null) {
                borrowChunk(regions, chunkX, chunkZ);
                return;
            }

            if (take(region)) {
                return;
            }
        }
    }

    /** Every region of the level, looping until a full pass adds nothing: the feed may create one while the pass runs, a fold may replace some. */
    public void borrowAll(LevelRegions regions) {
        int before;
        do {
            before = held.size();
            for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                take(region);
            }
        } while (held.size() != before);
    }

    /** Waits for a tick in flight. A region idle yet untakeable is owed a merge with one this thread holds: everything is returned so the regionizer folds, and the survivor is taken again. False once the region is dead. */
    private boolean take(Region<RegionTickData> region) {
        while (region.state() != RegionState.DEAD) {
            if (held.contains(region) || region.tryMarkTicking()) {
                held.add(region);
                return true;
            }

            if (region.state() != RegionState.TICKING && !held.isEmpty()) {
                releaseAll();
            } else {
                LockSupport.parkNanos(WAIT_NANOS);
            }
        }

        return false;
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

    /** What lands on a borrowed chunk waits in its inbox for the borrower; the pool owns the chunk again at the release. */
    private void borrowChunk(LevelRegions regions, int chunkX, int chunkZ) {
        if (!regions.live()) {
            return;
        }

        long key = ChunkPos.pack(chunkX, chunkZ);
        Long2ObjectOpenHashMap<RegionInbox> chunks = heldChunks.computeIfAbsent(regions, _ -> new Long2ObjectOpenHashMap<>());
        if (!chunks.containsKey(key)) {
            chunks.put(key, LevelChunks.of(regions.level()).owners().borrow(chunkX, chunkZ));
        }
    }

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

    /** What this thread holds runs here while it waits: the inboxes of its regions and of its chunks. */
    public int drainInboxes() {
        int drained = 0;
        for (Region<RegionTickData> region : held) {
            drained += region.data().inbox().drain();
        }

        for (Long2ObjectOpenHashMap<RegionInbox> chunks : heldChunks.values()) {
            for (RegionInbox inbox : chunks.values()) {
                drained += inbox.drain();
            }
        }

        return drained;
    }

    public int size() {
        return held.size();
    }
}
