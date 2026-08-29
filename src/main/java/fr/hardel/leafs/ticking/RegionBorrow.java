package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.ChunkMailbox;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.world.RegionTickBody;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/** What a thread holds for one piece of work, taken at first contact and kept until released: regions, and the chunks no region owns. The server thread borrows for a command, a ticking region for a chunk it waits on. */
public final class RegionBorrow {
    private static final ThreadLocal<RegionBorrow> CURRENT = new ThreadLocal<>();
    private static final long WAIT_NANOS = 50_000L;

    private final Region<RegionTickData> by;
    private final Set<Region<RegionTickData>> held = new LinkedHashSet<>();
    private final Map<LevelRegions, LongOpenHashSet> heldChunks = new LinkedHashMap<>();

    private RegionBorrow(Region<RegionTickData> by) {
        this.by = by;
    }

    /** The server thread borrows in its own name. */
    public static RegionBorrow enter() {
        return enter(null);
    }

    /** A ticking region borrows in its name: a partner owed to it by a pending merge is taken instead of waited for. */
    public static RegionBorrow enter(Region<RegionTickData> by) {
        RegionBorrow borrow = new RegionBorrow(by);
        CURRENT.set(borrow);
        return borrow;
    }

    public static void exit() {
        CURRENT.remove();
    }

    /** Reuses the borrow open on this thread, or opens one in {@code by}'s name and returns everything at the end. */
    public static <T> T hold(Region<RegionTickData> by, Function<RegionBorrow, T> body) {
        RegionBorrow current = CURRENT.get();
        if (current != null) {
            return body.apply(current);
        }

        RegionBorrow borrow = enter(by);
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

    /** The region of the position, waiting for its tick in flight, or the chunk itself when no region owns it. A merge pending with a region this thread holds waits for that one: everything is returned, the regionizer folds, the survivor is taken again. */
    public void borrow(LevelRegions regions, int chunkX, int chunkZ) {
        Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
        if (region == null) {
            borrowChunk(regions, chunkX, chunkZ);
            return;
        }

        while (region != null && !held.contains(region)) {
            if (region.tryMarkTicking(by)) {
                held.add(region);
                return;
            }

            if (region.state() != RegionState.TICKING && !held.isEmpty()) {
                releaseAll();
            } else {
                LockSupport.parkNanos(WAIT_NANOS);
            }

            region = regions.regionizer().regionAt(chunkX, chunkZ);
        }
    }

    /** Every region of the level, looping until a full pass adds nothing: the feed may create one while the pass runs. */
    public void borrowAll(LevelRegions regions) {
        int before;
        do {
            before = held.size();
            for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
                if (region.state() != RegionState.DEAD) {
                    borrowRegion(region);
                }
            }
        } while (held.size() != before);
    }

    /** Whether this thread holds the region of the position; a chunk no region owns is held by its claim in the mailbox. */
    public boolean holds(LevelRegions regions, int chunkX, int chunkZ) {
        Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
        return region != null && held.contains(region);
    }

    private void borrowChunk(LevelRegions regions, int chunkX, int chunkZ) {
        RegionTickBody body = regions.body();
        if (body == null) {
            return;
        }

        long key = ChunkPos.pack(chunkX, chunkZ);
        LongOpenHashSet chunks = heldChunks.computeIfAbsent(regions, _ -> new LongOpenHashSet());
        if (chunks.contains(key)) {
            return;
        }

        mailboxOf(body.level().getChunkSource().chunkMap).claim(key);
        chunks.add(key);
    }

    private void borrowRegion(Region<RegionTickData> region) {
        while (!held.contains(region) && region.state() != RegionState.DEAD) {
            if (region.tryMarkTicking(by)) {
                held.add(region);
                return;
            }

            LockSupport.parkNanos(WAIT_NANOS);
        }
    }

    public void releaseAll() {
        for (Region<RegionTickData> region : held) {
            region.markNotTicking();
        }

        held.clear();
        heldChunks.forEach((regions, chunks) -> {
            ChunkMailbox mailbox = mailboxOf(regions.body().level().getChunkSource().chunkMap);
            for (long key : chunks) {
                mailbox.releaseToWorkers(key);
            }
        });
        heldChunks.clear();
    }

    /** The mail of what this thread holds on that level: a chunk it waits for is delivered there, and nobody else drains what is held. */
    public int drainMail(ChunkMailbox mailbox, ChunkMap chunkMap) {
        int drained = 0;
        for (Region<RegionTickData> region : held) {
            drained += mailbox.drain(region, chunkMap);
        }

        LongOpenHashSet chunks = heldChunks.get(LevelRegions.of(chunkMap.level));
        if (chunks != null) {
            for (long key : chunks) {
                drained += mailbox.drain(key);
            }
        }

        return drained;
    }

    public int size() {
        return held.size();
    }

    private static ChunkMailbox mailboxOf(ChunkMap chunkMap) {
        return RegionChunkAccess.scheduling(chunkMap).mailbox();
    }
}
