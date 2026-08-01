package fr.hardel.leafs.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitySchedulerTest {
    private final EntityScheduler<String> scheduler = new EntityScheduler<>();
    private final List<String> executed = new ArrayList<>();

    @Test
    void delayIsClampedToOneTick() {
        assertTrue(scheduler.schedule(0, entity -> executed.add("ran on " + entity), null));

        assertEquals(List.of(), executed);
        scheduler.tick("entity");
        assertEquals(List.of("ran on entity"), executed);
    }

    @Test
    void delayCountsRegionTicks() {
        scheduler.schedule(3, entity -> executed.add("due"), null);

        scheduler.tick("entity");
        scheduler.tick("entity");
        assertEquals(List.of(), executed);
        scheduler.tick("entity");
        assertEquals(List.of("due"), executed);
    }

    @Test
    void tasksReceiveTheCurrentEntityInstance() {
        scheduler.schedule(2, entity -> executed.add(entity), null);

        scheduler.tick("old instance");
        scheduler.tick("new instance");

        assertEquals(List.of("new instance"), executed);
    }

    @Test
    void sameTickTasksRunInScheduleOrder() {
        scheduler.schedule(1, entity -> executed.add("first"), null);
        scheduler.schedule(1, entity -> executed.add("second"), null);

        scheduler.tick("entity");

        assertEquals(List.of("first", "second"), executed);
    }

    @Test
    void retireFiresPendingRetiredCallbacksAndRejectsNewTasks() {
        scheduler.schedule(5, entity -> executed.add("never"), () -> executed.add("retired1"));
        scheduler.schedule(5, entity -> executed.add("never"), () -> executed.add("retired2"));

        scheduler.retire();

        assertEquals(List.of("retired1", "retired2"), executed);
        assertTrue(scheduler.isRetired());
        assertFalse(scheduler.schedule(1, entity -> executed.add("never"), null));
        assertThrows(IllegalStateException.class, scheduler::retire);
    }

    @Test
    void tickingARetiredSchedulerIsANoOp() {
        scheduler.schedule(1, entity -> executed.add("never"), null);
        scheduler.retire();

        scheduler.tick("entity");

        assertEquals(List.of(), executed, "the registry may hand out a scheduler another thread just retired");
        assertFalse(scheduler.hasPendingTasks());
    }

    @Test
    void retiringMidTickStopsTheTasksBehindAndRetiresThem() {
        scheduler.schedule(1, entity -> {
            executed.add("first");
            scheduler.retire();
        }, () -> executed.add("retired first"));
        scheduler.schedule(1, entity -> executed.add("second"), () -> executed.add("retired second"));

        scheduler.tick("entity");

        assertEquals(List.of("first", "retired second"), executed, "a task due behind a retirement must not run against a destroyed entity");
    }

    @Test
    void everyTaskEndsInExactlyOneCallbackWhenRetirementRacesTheTick() throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int round = 0; round < 200; round++) {
            EntityScheduler<String> raced = new EntityScheduler<>();
            AtomicInteger ran = new AtomicInteger();
            AtomicInteger retired = new AtomicInteger();
            for (int task = 0; task < 5; task++) {
                raced.schedule(1, entity -> ran.incrementAndGet(), retired::incrementAndGet);
            }

            Thread retirer = new Thread(() -> {
                try {
                    raced.retire();
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            });
            retirer.start();
            try {
                raced.tick("entity");
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
            retirer.join();

            assertEquals(5, ran.get() + retired.get(), "a task must run or retire, never both and never neither");
        }

        assertNull(failure.get(), "neither the tick nor the retirement may throw");
    }
}
