package fr.hardel.leafs.ticking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class TickBarrierTest {
    private final TickBarrier barrier = new TickBarrier();

    @Test
    void raiseWaitsForTheActiveTick() throws InterruptedException {
        CountDownLatch tickStarted = new CountDownLatch(1);
        CountDownLatch letTickFinish = new CountDownLatch(1);
        AtomicBoolean tickFinished = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            barrier.enterTick();
            tickStarted.countDown();
            try {
                letTickFinish.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            tickFinished.set(true);
            barrier.exitTick();
        });
        worker.start();
        assertTrue(tickStarted.await(5, TimeUnit.SECONDS));

        Thread raiser = new Thread(barrier::raise);
        raiser.start();
        Thread.sleep(100);
        assertTrue(raiser.isAlive(), "raise must wait for the in-flight tick");

        letTickFinish.countDown();
        raiser.join(5_000);
        assertFalse(raiser.isAlive());
        assertTrue(tickFinished.get());
        barrier.drop();
        worker.join(5_000);
    }

    @Test
    void ticksParkWhileRaisedAndResumeOnDrop() throws InterruptedException {
        barrier.raise();
        CountDownLatch entered = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            barrier.enterTick();
            entered.countDown();
            barrier.exitTick();
        });
        worker.start();

        assertFalse(entered.await(150, TimeUnit.MILLISECONDS), "no tick may start while the barrier is raised");
        barrier.drop();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        worker.join(5_000);
    }

    @Test
    void noopPathStaysCheap() {
        long start = System.nanoTime();
        for (int i = 0; i < 5_000_000; i++) {
            barrier.enterTick();
            barrier.exitTick();
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMillis < 2_000, "5M lowered enter/exit pairs took " + elapsedMillis + "ms");
    }

    @Test
    void misuseCrashesEarly() {
        assertThrows(IllegalStateException.class, barrier::exitTick);
        assertThrows(IllegalStateException.class, barrier::drop);
        barrier.raise();
        assertThrows(IllegalStateException.class, barrier::raise);
    }
}
