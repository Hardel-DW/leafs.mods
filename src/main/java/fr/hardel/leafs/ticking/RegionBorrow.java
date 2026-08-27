package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.ChunkMailbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import net.minecraft.server.level.ChunkMap;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/** The regions the server thread holds for one piece of head work: taken at first contact, kept until released. A worker never waits for a region, this thread does. */
public final class RegionBorrow {
    private static final ThreadLocal<RegionBorrow> CURRENT = new ThreadLocal<>();
    private static final long WAIT_NANOS = 50_000L;

    private final Set<Region<RegionTickData>> held = new LinkedHashSet<>();

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

    /** Null on every thread that is not borrowing. */
    public static RegionBorrow current() {
        return CURRENT.get();
    }

    /**
     * Waits for the region's tick in flight. A refusal for a pending merge while this thread holds regions means the merge waits for one of
     * them: everything is returned, the regionizer folds, and the survivors are taken again at their next contact.
     */
    public void borrow(LevelRegions regions, int chunkX, int chunkZ) {
        Region<RegionTickData> region = regions.regionizer().regionAt(chunkX, chunkZ);
        while (region != null && !held.contains(region)) {
            if (region.tryMarkTicking()) {
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
            if (region.tryMarkTicking()) {
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
