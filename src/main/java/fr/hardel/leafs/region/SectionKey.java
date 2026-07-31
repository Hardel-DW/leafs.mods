package fr.hardel.leafs.region;

/** Packs a section coordinate pair into one long map key: x in the low 32 bits, z in the high 32. */
final class SectionKey {

    private SectionKey() {
    }

    static long pack(int sectionX, int sectionZ) {
        return ((long) sectionZ << 32) | (sectionX & 0xFFFFFFFFL);
    }

    static int x(long key) {
        return (int) key;
    }

    static int z(long key) {
        return (int) (key >>> 32);
    }
}
