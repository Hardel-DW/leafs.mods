package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.ticking.TickBarrier;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.function.Predicate;

/** Fabric server tick events emit with every region paused when subscribed; no subscriber, no pause. Server thread only. */
public final class FabricTickEventsBarrier {
    private final TickBarrier barrier;
    private final MinuteCounter pauses;
    private final Predicate<Event<?>> subscribed;
    private boolean held;

    public FabricTickEventsBarrier(TickBarrier barrier, MinuteCounter pauses) {
        this(barrier, pauses, FabricTickEventsBarrier::hasSubscribers);
    }

    FabricTickEventsBarrier(TickBarrier barrier, MinuteCounter pauses, Predicate<Event<?>> subscribed) {
        this.barrier = barrier;
        this.pauses = pauses;
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
        if (held || !subscribed.test(event))
            return;

        pauses.increment();
        barrier.raise();
        held = true;
    }

    private static boolean hasSubscribers(Event<?> event) {
        return ((FabricEventAccess) (Object) event).leafs$hasSubscribers();
    }
}
