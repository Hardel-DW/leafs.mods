package fr.hardel.leafs.global;

import fr.hardel.leafs.ticking.TickBarrier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class BarrierWindowTest {
    private final TickBarrier barrier = new TickBarrier();
    private final BarrierWindow window = new BarrierWindow(barrier);
    private final List<String> executed = new ArrayList<>();

    @Test
    void tasksRunInOrderAtTheNextWindow() {
        window.enqueue(() -> executed.add("first"));
        window.enqueue(() -> executed.add("second"));

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
        window.enqueue(() -> executed.add("window"));
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
        window.enqueue(() -> window.enqueue(() -> executed.add("requeued")));

        window.runGlobalPhase();
        assertEquals(List.of(), executed);

        window.runGlobalPhase();
        assertEquals(List.of("requeued"), executed);
    }

    @Test
    void throwingTaskPropagatesButTheBarrierDrops() {
        window.enqueue(() -> {
            throw new IllegalStateException("command block crash");
        });

        assertThrows(IllegalStateException.class, window::runGlobalPhase);

        barrier.enterTick();
        barrier.exitTick();
    }
}
