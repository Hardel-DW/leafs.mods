package fr.hardel.leafs.region;

/** One section of 2^shift chunks a side. The bits are the chunks that tick; every position of the section is owned. Bits are single-writer, the rest is under the regionizer's write lock. */
final class RegionSection<R> {
    private final long key;
    private final int indexShift;
    private final long[] chunkBits;
    private int chunkCount;
    private int nonEmptyNeighbours;
    private volatile Region<R> region;

    RegionSection(long key, int sectionShift, int nonEmptyNeighbours) {
        int chunksPerSection = 1 << (2 * sectionShift);
        this.key = key;
        this.indexShift = sectionShift;
        this.chunkBits = new long[(chunksPerSection + 63) >>> 6];
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

    void addChunk(int chunkX, int chunkZ) {
        int index = bitIndex(chunkX, chunkZ);
        long bit = 1L << (index & 63);
        if ((chunkBits[index >>> 6] & bit) != 0L) {
            throw new IllegalStateException("Chunk [" + chunkX + ", " + chunkZ + "] is already registered in section " + CoordinateKey.describe(key));
        }

        chunkBits[index >>> 6] |= bit;
        chunkCount++;
    }

    void removeChunk(int chunkX, int chunkZ) {
        int index = bitIndex(chunkX, chunkZ);
        long bit = 1L << (index & 63);
        if ((chunkBits[index >>> 6] & bit) == 0L) {
            throw new IllegalStateException("Chunk [" + chunkX + ", " + chunkZ + "] is not registered in section " + CoordinateKey.describe(key));
        }

        chunkBits[index >>> 6] &= ~bit;
        chunkCount--;
    }

    int nonEmptyNeighbours() {
        return nonEmptyNeighbours;
    }

    void gainedNonEmptyNeighbour() {
        nonEmptyNeighbours++;
    }

    void lostNonEmptyNeighbour() {
        if (nonEmptyNeighbours == 0) {
            throw new IllegalStateException("Non-empty neighbour count of section " + CoordinateKey.describe(key) + " dropped below zero");
        }

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

    void forEachPosition(Region.ChunkConsumer consumer) {
        int baseX = CoordinateKey.x(key) << indexShift;
        int baseZ = CoordinateKey.z(key) << indexShift;
        int side = 1 << indexShift;
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                consumer.accept(baseX + dx, baseZ + dz);
            }
        }
    }

    private int bitIndex(int chunkX, int chunkZ) {
        return CoordinateKey.index(chunkX, chunkZ, indexShift);
    }
}
