package fr.hardel.leafs.global;

import fr.hardel.leafs.ticking.TickBarrier;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.function.Predicate;

/**
 * Single-thread contract for the Fabric API server tick events: with at least one subscriber, the
 * emission runs with every region paused, and a server without subscribers pays nothing. Server
 * thread only, like the emissions it brackets.
 */
public final class FabricTickEventsBarrier {
    private final TickBarrier barrier;
    private final Predicate<Event<?>> subscribed;
    private boolean held;

    public FabricTickEventsBarrier(TickBarrier barrier) {
        this(barrier, FabricTickEventsBarrier::hasSubscribers);
    }

    FabricTickEventsBarrier(TickBarrier barrier, Predicate<Event<?>> subscribed) {
        this.barrier = barrier;
        this.subscribed = subscribed;
    }

    /** Right before the {@code START_SERVER_TICK} emission point. */
    public void openForTickStart() {
        openFor(ServerTickEvents.START_SERVER_TICK);
    }

    /** Right before the {@code END_SERVER_TICK} emission point. */
    public void openForTickEnd() {
        openFor(ServerTickEvents.END_SERVER_TICK);
    }

    /** After the emission point; without a matching open this is a no-op, so skipped-tick branches close safely. */
    public void close() {
        if (held) {
            held = false;
            barrier.drop();
        }
    }

    private void openFor(Event<?> event) {
        if (held || !subscribed.test(event)) {
            return;
        }

        barrier.raise();
        held = true;
    }

    private static boolean hasSubscribers(Event<?> event) {
        return ((FabricEventAccess) (Object) event).leafs$hasSubscribers();
    }
}
