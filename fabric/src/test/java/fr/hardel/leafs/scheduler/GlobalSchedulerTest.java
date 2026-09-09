package fr.hardel.leafs.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalSchedulerTest {
    private final GlobalScheduler scheduler = new GlobalScheduler();
    private final List<String> executed = new ArrayList<>();

    @Test
    void drainRunsTasksInSubmissionOrder() {
        scheduler.run(() -> executed.add("first"));
        scheduler.run(() -> executed.add("second"));

        scheduler.drain();
        assertEquals(List.of("first", "second"), executed);
        scheduler.drain();
        assertEquals(List.of("first", "second"), executed);
    }

    @Test
    void throwingTaskDoesNotStopTheDrain() {
        scheduler.run(() -> {
            throw new IllegalStateException("boom");
        });
        scheduler.run(() -> executed.add("survivor"));

        scheduler.drain();
        assertEquals(List.of("survivor"), executed);
    }

    /** 2026-09-06: the server thread pumped its queue inside a task waiting for a chunk, so the writes behind it ran first and erased a fresh nether portal. */
    @Test
    void aTaskThatPumpsWhileItWaitsNeverRunsTheTasksBehindIt() {
        scheduler.run(() -> {
            executed.add("write air");
            scheduler.drain();
            executed.add("air written");
        });
        scheduler.run(() -> executed.add("write portal"));

        scheduler.drain();
        assertEquals(List.of("write air", "air written", "write portal"), executed);
    }

    @Test
    void tasksQueuedDuringADrainWaitForTheNext() {

        scheduler.run(() -> scheduler.run(() -> executed.add("requeued")));

        scheduler.drain();
        assertEquals(List.of(), executed);
        scheduler.drain();
        assertEquals(List.of("requeued"), executed);
    }
}
