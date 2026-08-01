package fr.hardel.leafs.ticking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
            awaitQuietly(letTickFinish);
            tickFinished.set(true);
            barrier.exitTick();
        });
        worker.start();
        assertTrue(tickStarted.await(5, TimeUnit.SECONDS));

        CountDownLatch raiseReturned = new CountDownLatch(1);
        CountDownLatch letHolderDrop = new CountDownLatch(1);
        Thread raiser = new Thread(() -> {
            barrier.raise();
            raiseReturned.countDown();
            awaitQuietly(letHolderDrop);
            barrier.drop();
        });
        raiser.start();
        assertFalse(raiseReturned.await(150, TimeUnit.MILLISECONDS), "raise must wait for the in-flight tick");

        letTickFinish.countDown();
        assertTrue(raiseReturned.await(5, TimeUnit.SECONDS));
        assertTrue(tickFinished.get());

        letHolderDrop.countDown();
        raiser.join(5_000);
        worker.join(5_000);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
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
    }

    /** The two ways a single thread could park on itself for ever, holding the barrier up. */
    @Test
    void selfBlockingUseIsRejectedInsteadOfParked() {
        barrier.enterTick();
        assertThrows(IllegalStateException.class, barrier::raise, "raising from inside one's own tick would wait on oneself");
        barrier.exitTick();

        barrier.raise();
        assertThrows(IllegalStateException.class, barrier::enterTick, "ticking while holding the barrier would wait on oneself");
        barrier.drop();
    }

    @Test
    void aSecondRaiserWaitsForTheHolderInsteadOfCrashing() throws InterruptedException {
        barrier.raise();
        CountDownLatch raised = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            barrier.raise();
            raised.countDown();
            barrier.drop();
        });
        other.start();

        assertFalse(raised.await(150, TimeUnit.MILLISECONDS), "two holders must never own the window at once");
        barrier.drop();
        assertTrue(raised.await(5, TimeUnit.SECONDS));
        other.join(5_000);

        barrier.enterTick();
        barrier.exitTick();
    }

    @Test
    void theHolderMayNestRaises() throws InterruptedException {
        barrier.raise();
        barrier.raise();
        barrier.drop();

        CountDownLatch entered = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            barrier.enterTick();
            entered.countDown();
            barrier.exitTick();
        });
        worker.start();
        assertFalse(entered.await(150, TimeUnit.MILLISECONDS), "the outer raise still holds the barrier");

        barrier.drop();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        worker.join(5_000);
    }

    @Test
    void dropFromAnotherThreadIsRejected() throws InterruptedException {
        barrier.raise();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread stranger = new Thread(() -> {
            try {
                barrier.drop();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        stranger.start();
        stranger.join(5_000);

        assertInstanceOf(IllegalStateException.class, failure.get());
        barrier.drop();
    }

    /** F-C2: the interrupt used to leave the barrier raised for ever, freezing every later tick. */
    @Test
    void anInterruptedRaiseLeavesTheBarrierDown() throws InterruptedException {
        CountDownLatch tickStarted = new CountDownLatch(1);
        CountDownLatch letTickFinish = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            barrier.enterTick();
            tickStarted.countDown();
            awaitQuietly(letTickFinish);
            barrier.exitTick();
        });
        worker.start();
        assertTrue(tickStarted.await(5, TimeUnit.SECONDS));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread raiser = new Thread(() -> {
            try {
                barrier.raise();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        raiser.start();
        Thread.sleep(100);
        raiser.interrupt();
        raiser.join(5_000);

        assertInstanceOf(IllegalStateException.class, failure.get());
        letTickFinish.countDown();
        worker.join(5_000);

        CountDownLatch raised = new CountDownLatch(1);
        Thread next = new Thread(() -> {
            barrier.raise();
            raised.countDown();
            barrier.drop();
        });
        next.start();
        assertTrue(raised.await(5, TimeUnit.SECONDS), "the failed raise must have left the barrier down");
        next.join(5_000);
    }
}
