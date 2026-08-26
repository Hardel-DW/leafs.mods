package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.BarrierStats;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ticking.TickBarrier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class BarrierWindowTest {
    private final TickBarrier barrier = new TickBarrier();
    private final BarrierStats stats = new BarrierStats();
    private final BarrierWindow window = new BarrierWindow(barrier, stats, new DeferStats());
    private final List<String> executed = new ArrayList<>();

    private void enqueue(Runnable task) {
        window.enqueue(DeferReason.CONSOLE_COMMAND, task);
    }

    @Test
    void tasksRunInOrderAtTheNextWindow() {
        enqueue(() -> executed.add("first"));
        enqueue(() -> executed.add("second"));

        window.runGlobalPhase();

        assertEquals(List.of("first", "second"), executed);
        assertEquals(0, window.pendingCount());
    }

    @Test
    void emptyWindowNeverTouchesTheBarrier() {
        barrier.enterTick();

        window.runGlobalPhase();

        barrier.exitTick();
        assertEquals(List.of(), executed);
    }

    @Test
    void windowWaitsForInFlightTicksAndBlocksNewOnes() throws InterruptedException {
        enqueue(() -> executed.add("window"));
        barrier.enterTick();
        CountDownLatch windowDone = new CountDownLatch(1);
        Thread globalPhase = new Thread(() -> {
            window.runGlobalPhase();
            windowDone.countDown();
        });
        globalPhase.start();

        Thread.sleep(100);
        assertEquals(List.of(), executed, "the window must wait for the in-flight tick");

        barrier.exitTick();
        assertTrue(windowDone.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("window"), executed);

        barrier.enterTick();
        barrier.exitTick();
    }

    @Test
    void tasksQueuedDuringTheDrainWaitForTheNextWindow() {
        enqueue(() -> enqueue(() -> executed.add("requeued")));

        window.runGlobalPhase();
        assertEquals(List.of(), executed);

        window.runGlobalPhase();
        assertEquals(List.of("requeued"), executed);
    }

    @Test
    void throwingTaskPropagatesButTheBarrierDropsAndTheMarkerClears() {
        enqueue(() -> {
            throw new IllegalStateException("command block crash");
        });

        assertThrows(IllegalStateException.class, window::runGlobalPhase);

        assertFalse(window.isDraining());
        barrier.enterTick();
        barrier.exitTick();
    }

    /** Raising used to happen outside the try, so a failure there froze every later tick. */
    @Test
    void aFailingRaiseLeavesTheBarrierDown() throws InterruptedException {
        enqueue(() -> executed.add("never"));
        barrier.enterTick();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread globalPhase = new Thread(() -> {
            try {
                window.runGlobalPhase();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        globalPhase.start();

        Thread.sleep(100);
        globalPhase.interrupt();
        globalPhase.join(5_000);

        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(List.of(), executed);
        barrier.exitTick();

        window.runGlobalPhase();
        assertEquals(List.of("never"), executed, "the window must still work after the failed raise");
    }

    /** A deferred command block replays inside the window and sees isDraining, so a chain loop never fills the queue. */
    @Test
    void workDeferredFromInsideTheWindowRunsInline() {
        AtomicInteger executions = new AtomicInteger();
        Runnable[] unit = new Runnable[1];
        unit[0] = () -> {
            if (window.isDraining()) {
                executions.incrementAndGet();
                return;
            }

            enqueue(unit[0]);
        };

        assertFalse(window.isDraining());
        unit[0].run();
        assertEquals(0, executions.get());
        assertEquals(1, window.pendingCount());

        window.runGlobalPhase();

        assertEquals(1, executions.get());
        assertEquals(0, window.pendingCount());
        assertFalse(window.isDraining(), "the drain marker must not survive the window");
    }

    @Test
    void theShutdownWindowRunsPendingWorkAndDropsWhatItQueues() {
        enqueue(() -> {
            executed.add("last");
            enqueue(() -> executed.add("too late"));
        });

        window.runShutdownPhase();

        assertEquals(List.of("last"), executed);
        assertEquals(0, window.pendingCount());
    }
}
