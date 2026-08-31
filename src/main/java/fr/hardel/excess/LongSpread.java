package fr.hardel.excess;

/** Bijective mix for long keys before they reach a {@code ConcurrentHashMap}: {@code Long.hashCode} folds the halves, so packed coordinates collide into tree bins. */
final class LongSpread {
    private static final long MULTIPLIER = 0x9E3779B97F4A7C15L;
    private static final long INVERSE = 0xF1DE83E19937733DL;

    private LongSpread() {
    }

    static long mix(long key) {
        return key * MULTIPLIER;
    }

    static long unmix(long mixed) {
        return mixed * INVERSE;
    }
}
