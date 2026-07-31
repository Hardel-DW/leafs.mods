package fr.hardel.leafs.ticking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class LeafsWatchdogTest {

    @Test
    void stalledTickIsReportedWithItsRegionAndStack() throws InterruptedException {
        ConcurrentLinkedQueue<String> reports = new ConcurrentLinkedQueue<>();
        CountDownLatch reported = new CountDownLatch(1);
        LeafsWatchdog watchdog = new LeafsWatchdog(Duration.ofMillis(50), message -> {
            reports.add(message);
            reported.countDown();
        });
        watchdog.start();
        TestTickHandle handle = new TestTickHandle(7, tickCount -> { });
        CountDownLatch release = new CountDownLatch(1);
        Thread stalled = new Thread(() -> {
            watchdog.beginTick(handle);
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            watchdog.endTick(handle);
        }, "Stalled Test Thread");
        stalled.start();

        assertTrue(reported.await(5, TimeUnit.SECONDS), "the watchdog must report the stall");
        String report = reports.peek();
        assertTrue(report.contains("region #7"));
        assertTrue(report.contains("test:world"));
        assertTrue(report.contains("Stalled Test Thread"));
        assertTrue(report.contains("\tat "), "the report must include the stuck thread's stack");

        release.countDown();
        stalled.join(5_000);
        watchdog.stop();
    }
}
