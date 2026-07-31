package fr.hardel.leafs.ownership;

/**
 * Thread-ownership checks, enabled by {@code -ea} or {@code -Dleafs.asserts=true} and constant-folded
 * away otherwise. Violations crash early — never corrupt silently.
 */
public final class Ownership {
    public static final boolean CHECKS_ENABLED = computeEnabled();

    private Ownership() {
    }

    public static void assertGlobal() {
        if (CHECKS_ENABLED && !(RegionContext.current() instanceof RegionContext.Global)) {
            throw violation("the global phase");
        }
    }

    public static void assertRegionThread() {
        if (CHECKS_ENABLED && !(RegionContext.current() instanceof RegionContext.Region)) {
            throw violation("a region thread");
        }
    }

    private static OwnershipViolationException violation(String expected) {
        RegionContext current = RegionContext.current();
        return new OwnershipViolationException("Wrong-thread access: expected " + expected + ", but thread '"
            + Thread.currentThread().getName() + "' is "
            + (current == null ? "outside the region system" : current.describe()));
    }

    private static boolean computeEnabled() {
        String override = System.getProperty("leafs.asserts");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        
        return Ownership.class.desiredAssertionStatus();
    }
}
