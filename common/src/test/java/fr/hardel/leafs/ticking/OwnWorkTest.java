package fr.hardel.leafs.ticking;

import fr.hardel.leafs.global.GlobalScheduler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnWorkTest {
    private final GlobalScheduler diverted = new GlobalScheduler(Runnable::run);
    private final OwnWork work = new OwnWork(diverted::drain);

    @Test
    void aWaitRunsWhatTheThreadOwnsUntilDone() {
        AtomicInteger ran = new AtomicInteger();
        diverted.run(ran::incrementAndGet);
        diverted.run(ran::incrementAndGet);

        work.until(() -> ran.get() == 2);

        assertEquals(2, ran.get());
    }
}
