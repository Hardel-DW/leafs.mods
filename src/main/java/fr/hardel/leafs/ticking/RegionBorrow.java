package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.ChunkMailbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import net.minecraft.server.level.ChunkMap;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/** The regions a thread holds for one piece of work: taken at first contact, kept until released. The server thread borrows for a command, a ticking region for a chunk it waits on. */
public final class RegionBorrow {
    private static final ThreadLocal<RegionBorrow> CURRENT = new ThreadLocal<>();
    private static final long WAIT_NANOS = 50_000L;

    private final Region<RegionTickData> by;
    private final Set<Region<RegionTickData>> held = new LinkedHashSet<>();

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

    /** Waits for the region's tick in flight. A merge pending with a region this thread holds waits for that one: everything is returned, the regionizer folds, the survivor is taken again. */
    public void borrow(LevelRegions regions, int chunkX, int chunkZ) {
        Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
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
    }

    /** The mail of the held regions' chunks: a chunk this thread waits for is delivered there, and nobody else drains a held region. */
    public int drainMail(ChunkMailbox mailbox, ChunkMap chunkMap) {
        int drained = 0;
        for (Region<RegionTickData> region : held) {
            drained += mailbox.drain(region, chunkMap);
        }

        return drained;
    }

    public int size() {
        return held.size();
    }
}
