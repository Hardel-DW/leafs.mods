package fr.hardel.leafs.chunk;

import java.util.function.Supplier;

// A scope that reads chunks like a region: present-only, refusal plus demand ticket instead of a sync load that would freeze every region of the dimension.
public final class DegradedChunkReads {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DegradedChunkReads() {
    }

    public static void run(Runnable scope) {
        call(() -> {
            scope.run();
            return null;
        });
    }

    public static <T> T call(Supplier<T> scope) {
        ACTIVE.set(Boolean.TRUE);
        try {
            return scope.get();
        } finally {
            ACTIVE.set(Boolean.FALSE);
        }
    }

    // A nested block that must run raw, like the final teleport of a portal task whose search ran degraded.
    public static void escape(Runnable block) {
        boolean active = ACTIVE.get();
        ACTIVE.set(Boolean.FALSE);
        try {
            block.run();
        } finally {
            ACTIVE.set(active);
        }
    }

    public static boolean active() {
        return ACTIVE.get();
    }
}
