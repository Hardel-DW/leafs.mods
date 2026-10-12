package fr.hardel.leafs.ticking;

import fr.hardel.leafs.LeafsConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The watchdog is checked at a chosen instant past its thresholds, never by waiting for its own thread. */
class LeafsWatchdogTest {
    private static final long WARN = Duration.ofSeconds(1).toNanos();
    private static final long KILL = Duration.ofSeconds(2).toNanos();
    private static final long KILL_DISABLED = 0L;

    private final List<String> reports = new ArrayList<>();
    private final List<LeafsWatchdog.Stall> kills = new ArrayList<>();
    private final Map<Thread, String> stalledWaits = new HashMap<>();

    private LeafsWatchdog watchdog(long warnNanos, long killNanos) {
        return new LeafsWatchdog(warnNanos, () -> killNanos, _ -> new HashMap<>(stalledWaits), reports::add, kills::add);
    }

    @Test
    void aStalledTickIsReportedWithItsRegionAndStack() {
        LeafsWatchdog watchdog = watchdog(WARN, KILL_DISABLED);
        watchdog.beginTick(new TestTickHandle(7, () -> { }));

        watchdog.check(System.nanoTime() + WARN);

        assertEquals(1, reports.size());
        String report = reports.getFirst();
        assertTrue(report.contains("region #7"));
        assertTrue(report.contains("test:world"));
        assertTrue(report.contains(Thread.currentThread().getName()));
        assertTrue(report.contains("\tat "), "the report must include the stuck thread's stack");
    }

    /** B25: the first report subtracted Long.MIN_VALUE from now, overflowed, and never fired. */
    @Test
    void aStalledWaitOffTheTickUnitsIsReportedAtOnce() {
        LeafsWatchdog watchdog = watchdog(WARN, KILL_DISABLED);
        stalledWaits.put(Thread.currentThread(), "Chunk wait stalled on a mod thread");

        watchdog.check(System.nanoTime());

        assertEquals(1, reports.size());
        assertTrue(reports.getFirst().contains("mod thread"));
    }

    @Test
    void aTickPastTheKillThresholdRunsTheKillerOnce() {
        LeafsWatchdog watchdog = watchdog(WARN, KILL);
        watchdog.beginTick(new TestTickHandle(9, () -> { }));
        long now = System.nanoTime();

        watchdog.check(now + KILL);
        watchdog.check(now + 2 * KILL);

        assertEquals(1, kills.size(), "the killer must run once per stall");
        LeafsWatchdog.Stall stall = kills.getFirst();
        assertTrue(stall.summary().contains("region #9"));
        assertSame(Thread.currentThread(), stall.thread(), "the killer must receive the stuck thread for the dump");
    }

    @Test
    void aDisabledWarnStaysSilentAndStillKills() {
        LeafsConfig.Debug debug = new LeafsConfig.Debug(LeafsConfig.Debug.DISABLED, false, LeafsConfig.Debug.DISABLED);
        LeafsWatchdog watchdog = watchdog(debug.watchdogWarnNanos(), KILL);
        watchdog.beginTick(new TestTickHandle(3, () -> { }));

        watchdog.check(System.nanoTime() + KILL);

        assertEquals(1, kills.size(), "the kill threshold must still fire");
        assertTrue(reports.isEmpty(), "a disabled warn must never report");
    }
}
