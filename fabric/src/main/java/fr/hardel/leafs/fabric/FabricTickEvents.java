package fr.hardel.leafs.fabric;

import fr.hardel.leafs.global.TickEventBorrow;
import fr.hardel.leafs.metrics.MinuteCounter;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.function.Predicate;

/** The two Fabric server tick events, borrowed only when someone subscribed. */
public final class FabricTickEvents {
    private final TickEventBorrow borrow;
    private final Predicate<Event<?>> subscribed;

    public FabricTickEvents(MinuteCounter borrows) {
        this(borrows, FabricTickEvents::hasSubscribers);
    }

    FabricTickEvents(MinuteCounter borrows, Predicate<Event<?>> subscribed) {
        this.borrow = new TickEventBorrow(borrows);
        this.subscribed = subscribed;
    }

    public void openForTickStart() {
        openFor(ServerTickEvents.START_SERVER_TICK);
    }

    public void openForTickEnd() {
        openFor(ServerTickEvents.END_SERVER_TICK);
    }

    public void close() {
        borrow.close();
    }

    private void openFor(Event<?> event) {
        if (subscribed.test(event)) {
            borrow.open();
        }
    }

    private static boolean hasSubscribers(Event<?> event) {
        return ((FabricEventAccess) (Object) event).leafs$hasSubscribers();
    }
}
