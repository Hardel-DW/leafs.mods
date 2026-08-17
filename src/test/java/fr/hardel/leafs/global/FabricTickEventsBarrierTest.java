package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.ticking.TickBarrier;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The barrier itself is exercised through {@link TickBarrier}'s own contract: a thread that holds it
 * cannot enter a tick, so {@code enterTick} throwing is the deterministic probe for "held".
 */
@Timeout(10)
class FabricTickEventsBarrierTest {

    private final TickBarrier barrier = new TickBarrier();
    private final List<Event<?>> probed = new ArrayList<>();

    private FabricTickEventsBarrier barrier(boolean subscribed) {
        Predicate<Event<?>> probe = event -> {
            probed.add(event);
            return subscribed;
        };

        return new FabricTickEventsBarrier(barrier, new MinuteCounter(), probe);
    }

    @Test
    void subscribedEventRaisesTheBarrierUntilClose() {
        FabricTickEventsBarrier events = barrier(true);

        events.openForTickStart();
        assertThrows(IllegalStateException.class, barrier::enterTick, "the emission window must hold the barrier");

        events.close();
        assertDoesNotThrow(() -> {
            barrier.enterTick();
            barrier.exitTick();
        });
    }

    @Test
    void bothWindowsProbeTheirOwnEvent() {
        FabricTickEventsBarrier events = barrier(true);

        events.openForTickStart();
        events.close();
        events.openForTickEnd();
        events.close();

        assertEquals(List.of(ServerTickEvents.START_SERVER_TICK, ServerTickEvents.END_SERVER_TICK), probed);
    }

    @Test
    void withoutSubscribersTheBarrierNeverRises() {
        FabricTickEventsBarrier events = barrier(false);

        events.openForTickStart();
        assertDoesNotThrow(() -> {
            barrier.enterTick();
            barrier.exitTick();
        });
        events.close();
    }

    @Test
    void closeWithoutOpenIsANoOp() {
        FabricTickEventsBarrier events = barrier(true);

        assertDoesNotThrow(events::close);
    }

    @Test
    void doubleOpenHoldsOnceAndReleasesOnOneClose() {
        FabricTickEventsBarrier events = barrier(true);

        events.openForTickStart();
        events.openForTickStart();
        events.close();

        assertDoesNotThrow(() -> {
            barrier.enterTick();
            barrier.exitTick();
        });
    }
}
