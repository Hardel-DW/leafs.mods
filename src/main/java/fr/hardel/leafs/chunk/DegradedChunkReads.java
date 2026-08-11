package fr.hardel.leafs.chunk;

import java.util.function.Supplier;

/**
 * A serial-phase scope that must read chunks like a region: present chunks only, a refusal and a
 * demand ticket instead of a sync load. The custom spawners probe blocks near a random player who
 * may stand in ungenerated terrain; vanilla would block the serial thread on generation there,
 * holding the exclusion and freezing every region of the dimension.
 */
public final class DegradedChunkReads {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DegradedChunkReads() {
    }

    public static void run(Runnable scope) {
        ACTIVE.set(Boolean.TRUE);
        try {
            scope.run();
        } finally {
            ACTIVE.set(Boolean.FALSE);
        }
    }

    public static <T> T call(Supplier<T> scope) {
        ACTIVE.set(Boolean.TRUE);
        try {
            return scope.get();
        } finally {
            ACTIVE.set(Boolean.FALSE);
        }
    }

    public static boolean active() {
        return ACTIVE.get();
    }
}
