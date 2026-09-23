package fr.hardel.leafs.region;

public final class CoordinateKey {

    private CoordinateKey() {
    }

    public static long pack(int sectionX, int sectionZ) {
        return ((long) sectionZ << 32) | (sectionX & 0xFFFFFFFFL);
    }

    // Used by the Leafs Debug mod
    public static int x(long key) {
        return (int) key;
    }

    // Used by the Leafs Debug mod
    public static int z(long key) {
        return (int) (key >>> 32);
    }

    public static int index(int chunkX, int chunkZ, int sectionShift) {
        int mask = (1 << sectionShift) - 1;
        return ((chunkZ & mask) << sectionShift) | (chunkX & mask);
    }

    public static String describe(long key) {
        return "[" + x(key) + ", " + z(key) + "]";
    }
}
