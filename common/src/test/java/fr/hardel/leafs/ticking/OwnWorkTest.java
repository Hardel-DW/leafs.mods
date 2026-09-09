package fr.hardel.leafs.ticking;

import fr.hardel.leafs.scheduler.GlobalScheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnWorkTest {
    private final GlobalScheduler diverted = new GlobalScheduler();
    private final OwnWork work = new OwnWork(diverted::drain);

    @Test
    void aWaitRunsWhatTheThreadOwnsUntilDone() {
        AtomicInteger ran = new AtomicInteger();
        diverted.run(ran::incrementAndGet);
        diverted.run(ran::incrementAndGet);

        work.until(() -> ran.get() == 2);

        assertEquals(2, ran.get());
    }

    /** 2026-09-09: a mod queued one server task per chunk, each borrowing the player's region; the head pumping vanilla's queue ran the next head inside its own wait, and the stack ended. */
    @Test
    void aHeadWaitingInsideItsTaskLeavesTheNextHeadToTheOuterDrain() {
        List<String> order = new ArrayList<>();
        AtomicInteger checks = new AtomicInteger();
        diverted.run(() -> {
            order.add("first begins");
            work.until(() -> checks.incrementAndGet() > 1);
            order.add("first ends");
        });
        diverted.run(() -> order.add("second"));

        work.until(() -> order.size() == 3);

        assertEquals(List.of("first begins", "first ends", "second"), order);
    }
}
