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

    public static String describe(long key) {
        return "[" + x(key) + ", " + z(key) + "]";
    }
}
