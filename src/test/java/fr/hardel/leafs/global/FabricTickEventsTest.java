package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricTickEventsTest {
    private final List<Event<?>> probed = new ArrayList<>();
    private final MinuteCounter borrows = new MinuteCounter();

    @AfterEach
    void exitBorrow() {
        RegionBorrow.exit();
    }

    private FabricTickEvents events(boolean subscribed) {
        Predicate<Event<?>> probe = event -> {
            probed.add(event);
            return subscribed;
        };

        return new FabricTickEvents(borrows, probe);
    }

    @Test
    void aSubscribedEventBorrowsUntilClose() {
        FabricTickEvents events = events(true);

        events.openForTickStart();
        assertNotNull(RegionBorrow.current(), "the emission runs as the borrowing server thread");
        assertEquals(1, borrows.perMinute());

        events.close();
        assertNull(RegionBorrow.current());
    }

    @Test
    void bothEmissionsProbeTheirOwnEvent() {
        FabricTickEvents events = events(true);

        events.openForTickStart();
        events.close();
        events.openForTickEnd();
        events.close();

        assertEquals(List.of(ServerTickEvents.START_SERVER_TICK, ServerTickEvents.END_SERVER_TICK), probed);
    }

    @Test
    void withoutSubscribersNothingBorrows() {
        FabricTickEvents events = events(false);

        events.openForTickStart();
        assertNull(RegionBorrow.current());
        assertEquals(0, borrows.perMinute());
        events.close();
    }

    @Test
    void closeWithoutOpenIsANoOpAndADoubleOpenBorrowsOnce() {
        FabricTickEvents events = events(true);

        assertDoesNotThrow(events::close);
        events.openForTickStart();
        events.openForTickStart();
        assertEquals(1, borrows.perMinute());
        events.close();
        assertNull(RegionBorrow.current());
    }
}
