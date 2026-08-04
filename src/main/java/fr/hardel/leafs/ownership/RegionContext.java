package fr.hardel.leafs.ownership;

/**
 * What the current thread is, from the region system's point of view. Threads without a context
 * (Netty, worker pools, external mod threads) own nothing and must go through the schedulers.
 */
public sealed interface RegionContext {

    Global GLOBAL = new Global();

    record Global() implements RegionContext {
        @Override
        public String describe() {
            return "global phase";
        }
    }

    /** Mid-tick of one tick unit: it owns its slice of one level and reaches everything else through the schedulers. */
    sealed interface UnitTick extends RegionContext {
        long id();

        String dimension();
    }

    record Region(long id, String dimension) implements UnitTick {
        @Override
        public String describe() {
            return "region #" + id + " in " + dimension;
        }
    }

    record LevelSerial(long id, String dimension) implements UnitTick {
        @Override
        public String describe() {
            return "level-serial unit #" + id + " in " + dimension;
        }
    }

    String describe();

    static RegionContext current() {
        return RegionContextHolder.CURRENT.get();
    }

    static void enter(RegionContext context) {
        if (Ownership.CHECKS_ENABLED && RegionContextHolder.CURRENT.get() != null) {
            throw new IllegalStateException("Thread '" + Thread.currentThread().getName() + "' entered " + context.describe() + " while already in " + RegionContextHolder.CURRENT.get().describe());
        }
        
        RegionContextHolder.CURRENT.set(context);
    }

    static void exit() {
        RegionContextHolder.CURRENT.remove();
    }
}

final class RegionContextHolder {
    static final ThreadLocal<RegionContext> CURRENT = new ThreadLocal<>();

    private RegionContextHolder() {
    }
}
