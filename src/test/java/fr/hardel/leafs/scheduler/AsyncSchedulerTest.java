package fr.hardel.leafs.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncSchedulerTest {

    @Test
    void runsOnANamedWorkerThread() throws InterruptedException {
        AsyncScheduler scheduler = new AsyncScheduler(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        scheduler.run(() -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("Leafs Async Worker #"), "got thread: " + threadName.get());
        assertTrue(scheduler.shutdown(Duration.ofSeconds(5)));
    }
}
