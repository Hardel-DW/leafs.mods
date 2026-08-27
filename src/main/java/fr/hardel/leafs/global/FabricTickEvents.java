package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.function.Predicate;

/** A subscribed Fabric server tick event runs with the server thread borrowing: a mod that touches nothing stops nobody, one that touches a chunk or an entity takes its region at contact. Server thread only. */
public final class FabricTickEvents {
    private final MinuteCounter borrows;
    private final Predicate<Event<?>> subscribed;
    private boolean open;

    public FabricTickEvents(MinuteCounter borrows) {
        this(borrows, FabricTickEvents::hasSubscribers);
    }

    FabricTickEvents(MinuteCounter borrows, Predicate<Event<?>> subscribed) {
        this.borrows = borrows;
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
        if (open) {
            open = false;
            RegionBorrow.current().releaseAll();
            RegionBorrow.exit();
        }
    }

    private void openFor(Event<?> event) {
        if (open || !subscribed.test(event)) {
            return;
        }

        borrows.increment();
        RegionBorrow.enter();
        open = true;
    }

    private static boolean hasSubscribers(Event<?> event) {
        return ((FabricEventAccess) (Object) event).leafs$hasSubscribers();
    }
}
