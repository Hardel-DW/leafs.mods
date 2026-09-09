package fr.hardel.leafs.ticking;

/** What the current thread is for the region system. A thread without context (Netty, pools, mods) owns nothing. */
public sealed interface RegionContext {

    record Region(long id, String dimension) implements RegionContext {
        @Override
        public String describe() {
            return "region #" + id + " in " + dimension;
        }
    }

    record LevelSerial(long id, String dimension) implements RegionContext {
        @Override
        public String describe() {
            return "level-serial unit #" + id + " in " + dimension;
        }
    }

    long id();

    String dimension();

    String describe();

    static RegionContext current() {
        return RegionContextHolder.CURRENT.get();
    }

    static void enter(RegionContext context) {
        RegionContext previous = RegionContextHolder.CURRENT.get();
        if (previous != null) {
            throw new IllegalStateException("Thread '" + Thread.currentThread().getName() + "' entered " + context.describe() + " while already in " + previous.describe());
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
