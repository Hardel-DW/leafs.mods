package fr.hardel.leafs.region;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

final class RegionSection<R> {
    private final long key;
    private final int sectionShift;
    private int chunkCount;
    private int nonEmptyNeighbours;
    private volatile Region<R> region;
    private final AtomicBoolean changed = new AtomicBoolean();

    RegionSection(long key, int sectionShift, int nonEmptyNeighbours) {
        this.key = key;
        this.sectionShift = sectionShift;
        this.nonEmptyNeighbours = nonEmptyNeighbours;
    }

    long key() {
        return key;
    }

    boolean isEmpty() {
        return chunkCount == 0;
    }

    int chunkCount() {
        return chunkCount;
    }

    void addChunk() {
        chunkCount++;
    }

    void removeChunk() {
        chunkCount--;
    }

    int nonEmptyNeighbours() {
        return nonEmptyNeighbours;
    }

    void gainedNonEmptyNeighbour() {
        nonEmptyNeighbours++;
    }

    void lostNonEmptyNeighbour() {
        nonEmptyNeighbours--;
    }

    Region<R> region() {
        return region;
    }

    void setRegion(Region<R> region) {
        this.region = region;
    }

    void clearRegion() {
        this.region = null;
    }

    /** Whether this call is the one that marks the section: a marked section waits once in the queue of its region. */
    boolean markChanged() {
        return changed.compareAndSet(false, true);
    }

    void readAgain() {
        changed.set(false);
    }

    void forEachChunkKey(LongConsumer consumer) {
        int baseX = CoordinateKey.x(key) << sectionShift;
        int baseZ = CoordinateKey.z(key) << sectionShift;
        int side = 1 << sectionShift;
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                consumer.accept(CoordinateKey.pack(baseX + dx, baseZ + dz));
            }
        }
    }
}
