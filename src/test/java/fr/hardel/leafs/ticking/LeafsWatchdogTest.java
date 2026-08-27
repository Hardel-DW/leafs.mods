package fr.hardel.leafs.ticking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class LeafsWatchdogTest {
    private static final Duration KILL_DISABLED = Duration.ZERO;

    private final ConcurrentLinkedQueue<String> reports = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LeafsWatchdog.Stall> kills = new ConcurrentLinkedQueue<>();
    private final CountDownLatch reported = new CountDownLatch(1);
    private final CountDownLatch killed = new CountDownLatch(1);

    private LeafsWatchdog watchdog(Duration warnAfter, Duration killAfter) {
        return new LeafsWatchdog(warnAfter, () -> killAfter.toNanos(), message -> {
            reports.add(message);
            reported.countDown();
        }, stall -> {
            kills.add(stall);
            killed.countDown();
        });
    }

    @Test
    void stalledTickIsReportedWithItsRegionAndStack() throws InterruptedException {
        LeafsWatchdog watchdog = watchdog(Duration.ofMillis(50), KILL_DISABLED);
        watchdog.start();
        TestTickHandle handle = new TestTickHandle(7, () -> { });
        CountDownLatch release = new CountDownLatch(1);
        Thread stalled = stalledTick(watchdog, handle, release);

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

    @Test
    void tickPastTheKillThresholdRunsTheKillerOnce() throws InterruptedException {
        LeafsWatchdog watchdog = watchdog(Duration.ofMillis(40), Duration.ofMillis(120));
        watchdog.start();
        TestTickHandle handle = new TestTickHandle(9, () -> { });
        CountDownLatch release = new CountDownLatch(1);
        Thread stalled = stalledTick(watchdog, handle, release);

        assertTrue(killed.await(5, TimeUnit.SECONDS), "the watchdog must hand the stall to the killer");
        Thread.sleep(200);
        assertEquals(1, kills.size(), "the killer must run once per stall");
        LeafsWatchdog.Stall stall = kills.peek();
        assertTrue(stall.summary().contains("region #9"));
        assertSame(stalled, stall.thread(), "the killer must receive the stuck thread for the dump");

        release.countDown();
        stalled.join(5_000);
        watchdog.stop();
    }

    @Test
    void shutdownPastTheDeadlineRunsTheKillerWithTheStoppingThread() throws InterruptedException {
        LeafsWatchdog watchdog = watchdog(Duration.ofSeconds(5), Duration.ofSeconds(10));
        watchdog.armShutdownDeadline(Duration.ofMillis(50));

        assertTrue(killed.await(5, TimeUnit.SECONDS), "an expired deadline must reach the killer");
        LeafsWatchdog.Stall stall = kills.peek();
        assertTrue(stall.summary().contains("shutdown"));
        assertSame(Thread.currentThread(), stall.thread(), "the dump must point at the thread that ran stopServer");
    }

    @Test
    void disarmedDeadlineNeverKills() throws InterruptedException {
        LeafsWatchdog watchdog = watchdog(Duration.ofSeconds(5), Duration.ofSeconds(10));
        watchdog.armShutdownDeadline(Duration.ofMillis(150));
        watchdog.disarmShutdownDeadline();

        Thread.sleep(400);
        assertTrue(kills.isEmpty(), "a disarmed deadline must not kill the JVM");
    }

    @Test
    void disabledKillThresholdArmsNothing() throws InterruptedException {
        LeafsWatchdog watchdog = watchdog(Duration.ofSeconds(5), KILL_DISABLED);
        watchdog.armShutdownDeadline(Duration.ofMillis(50));

        Thread.sleep(300);
        assertTrue(kills.isEmpty(), "kill 0 must disable the shutdown deadline too");
    }

    private static Thread stalledTick(LeafsWatchdog watchdog, TestTickHandle handle, CountDownLatch release) {
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

        return stalled;
    }
}
