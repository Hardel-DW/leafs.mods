package fr.hardel.leafs.region;

/**
 * One grid section of 2^shift x 2^shift chunks. Chunk bits are single-writer (chunk-system guarantee);
 * {@code nonEmptyNeighbours} and {@code region} are guarded by the regionizer's write lock.
 */
final class RegionSection<R> {
    private final long key;
    private final int coordinateMask;
    private final int indexShift;
    private final long[] chunkBits;
    private int chunkCount;
    private int nonEmptyNeighbours;
    private volatile Region<R> region;

    RegionSection(long key, int sectionShift, int nonEmptyNeighbours) {
        int chunksPerSection = 1 << (2 * sectionShift);
        this.key = key;
        this.coordinateMask = (1 << sectionShift) - 1;
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

    void forEachChunk(Region.ChunkConsumer consumer) {
        int baseX = CoordinateKey.x(key) << indexShift;
        int baseZ = CoordinateKey.z(key) << indexShift;
        for (int word = 0; word < chunkBits.length; word++) {
            long bits = chunkBits[word];
            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int index = (word << 6) | bit;
                consumer.accept(baseX + (index & coordinateMask), baseZ + (index >>> indexShift));
            }
        }
    }

    private int bitIndex(int chunkX, int chunkZ) {
        return ((chunkZ & coordinateMask) << indexShift) | (chunkX & coordinateMask);
    }
}
