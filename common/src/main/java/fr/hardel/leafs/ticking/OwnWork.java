package fr.hardel.leafs.ticking;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Vanilla's server queue never runs in a Leafs wait: a head pumping it runs the next head, whose wait nests inside the first. */
public final class OwnWork {
    private static final long PARK_NANOS = 50_000L;
    private final BooleanSupplier pump;

    public OwnWork(BooleanSupplier pump) {
        this.pump = pump;
    }

    public void until(BooleanSupplier done) {
        RegionBorrow borrow = RegionBorrow.current();
        while (!done.getAsBoolean()) {
            boolean pumped = pump.getAsBoolean();
            boolean drained = borrow != null && borrow.drainInboxes() > 0;
            if (!pumped && !drained) {
                LockSupport.parkNanos(PARK_NANOS);
            }
        }
    }
}
