package fr.hardel.leafs.region;

/** Packs a section coordinate pair into one long map key: x in the low 32 bits, z in the high 32. */
public final class CoordinateKey {

    private CoordinateKey() {
    }

    public static long pack(int sectionX, int sectionZ) {
        return ((long) sectionZ << 32) | (sectionX & 0xFFFFFFFFL);
    }

    public static int x(long key) {
        return (int) key;
    }

    public static int z(long key) {
        return (int) (key >>> 32);
    }

    /** Slot of a chunk inside its section, row by row: what a section-local array is indexed with. */
    public static int index(int chunkX, int chunkZ, int sectionShift) {
        int mask = (1 << sectionShift) - 1;
        return ((chunkZ & mask) << sectionShift) | (chunkX & mask);
    }

    public static String describe(long key) {
        return "[" + x(key) + ", " + z(key) + "]";
    }
}
