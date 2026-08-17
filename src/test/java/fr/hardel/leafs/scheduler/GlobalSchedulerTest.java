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

    @Test
    void tasksQueuedDuringADrainWaitForTheNext() {
        scheduler.run(() -> scheduler.run(() -> executed.add("requeued")));

        scheduler.drain();
        assertEquals(List.of(), executed);
        scheduler.drain();
        assertEquals(List.of("requeued"), executed);
    }
}
