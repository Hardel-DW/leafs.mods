package fr.hardel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** A test waits for a state, never for a duration: a bug that blocks shows as a hang, never as a timeout that a slow machine could also reach. */
public final class TestThreads {
    private TestThreads() {
    }

    public static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    public static void until(BooleanSupplier state) {
        while (!state.getAsBoolean()) {
            Thread.onSpinWait();
        }
    }

    /** The thread waits on what the test holds, or it ended without waiting. */
    public static void awaitParked(Thread thread) {
        until(() -> switch (thread.getState()) {
            case BLOCKED, WAITING, TIMED_WAITING, TERMINATED -> true;
            case NEW, RUNNABLE -> false;
        });
    }

    public static CountDownLatch occupy(Executor singleWorker) {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        singleWorker.execute(() -> {
            started.countDown();
            await(gate);
        });
        await(started);
        return gate;
    }
}
