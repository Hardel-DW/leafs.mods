package fr.hardel.leafs.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertThrows(IllegalStateException.class, () -> scheduler.tick("entity"));
        assertThrows(IllegalStateException.class, scheduler::retire);
    }
}
