package fr.hardel.leafs.ticking;

import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Vanilla's server queue never runs in a Leafs wait: a head pumping it runs the next head, whose wait nests inside the first. */
public final class OwnWork {
    public static final BooleanSupplier NO_HELP = () -> false;
    private static final long PARK_NANOS = 50_000L;
    private final BooleanSupplier pump;

    public OwnWork(BooleanSupplier pump) {
        this.pump = pump;
    }

    public void until(BooleanSupplier done, BooleanSupplier help) {
        RegionBorrow borrow = RegionBorrow.current();
        Thread waiter = Thread.currentThread();
        while (!done.getAsBoolean()) {
            if (waiter.isInterrupted()) {
                throw new CancellationException("%s was interrupted during a Leafs wait".formatted(waiter.getName()));
            }

            boolean pumped = pump.getAsBoolean();
            boolean drained = borrow != null && borrow.drainInboxes() > 0;
            if (!pumped && !drained && !help.getAsBoolean()) {
                LockSupport.parkNanos(PARK_NANOS);
            }
        }
    }
}
