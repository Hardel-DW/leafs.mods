package fr.hardel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestThreads {
    private TestThreads() {
    }

    public static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "the latch never opened");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
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
