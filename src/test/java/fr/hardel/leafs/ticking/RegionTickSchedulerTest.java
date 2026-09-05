package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.ModAttribution;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTickSchedulerTest {
    private RegionTickScheduler scheduler;

    @AfterEach
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private RegionTickScheduler createScheduler(int threads, Path crashDirectory) {
        scheduler = new RegionTickScheduler(threads, false, new LeafsWatchdog(Duration.ofSeconds(60), () -> 0L, message -> { }, stall -> { }), new RegionCrashWriter(crashDirectory, new ModAttribution(_ -> Optional.empty())), (handle, throwable) -> { });
        return scheduler;
    }

    @Test
    void regionThreadNamesScopeTheWorkerDuringItsTick(@TempDir Path crashDirectory) throws InterruptedException {
        scheduler = new RegionTickScheduler(1, true, new LeafsWatchdog(Duration.ofSeconds(60), () -> 0L, message -> { }, stall -> { }), new RegionCrashWriter(crashDirectory, new ModAttribution(_ -> Optional.empty())), (handle, throwable) -> { });
        scheduler.start();
        CountDownLatch ticked = new CountDownLatch(1);
        AtomicReference<String> nameDuringTick = new AtomicReference<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        TestTickHandle handle = new TestTickHandle(4, () -> {
            worker.set(Thread.currentThread());
            nameDuringTick.set(Thread.currentThread().getName());
            ticked.countDown();
        });

        scheduler.schedule(handle);

        assertTrue(ticked.await(5, TimeUnit.SECONDS));
        handle.cancel();
        assertEquals("R#4 test:world", nameDuringTick.get());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!worker.get().getName().startsWith("Leafs Region Worker") && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }

        assertTrue(worker.get().getName().startsWith("Leafs Region Worker"));
    }

    @Test
    void attachedTickRunsWithTheRegionContext(@TempDir Path crashDirectory) {
        RegionTickScheduler attached = createScheduler(1, crashDirectory);
        AtomicReference<RegionContext> observed = new AtomicReference<>();
        TestTickHandle handle = new TestTickHandle(7, () -> observed.set(RegionContext.current()));

        attached.runAttached(handle);

        assertEquals("region #7 in test:world", observed.get().describe());
        assertNull(RegionContext.current());
        assertEquals(1, handle.currentTick());
    }

    @Test
    void attachedCrashWritesTheRegionReportAndPropagates(@TempDir Path crashDirectory) throws IOException {
        RegionTickScheduler attached = createScheduler(1, crashDirectory);
        TestTickHandle handle = new TestTickHandle(9, () -> {
            throw new IllegalStateException("boom");
        });

        assertThrows(IllegalStateException.class, () -> attached.runAttached(handle));

        assertNull(RegionContext.current());
        try (Stream<Path> files = Files.list(crashDirectory)) {
            List<Path> reports = files.toList();
            assertEquals(1, reports.size());
            String content = Files.readString(reports.getFirst());
            assertTrue(content.contains("Region: #9"));
            assertTrue(content.contains("IllegalStateException: boom"));
        }
    }

    /** A unit that recovers keeps the report, swallows the failure and stays schedulable; the failure policy never hears of it. */
    @Test
    void aRecoveredCrashWritesTheReportAndDoesNotPropagate(@TempDir Path crashDirectory) throws IOException {
        RegionTickScheduler attached = createScheduler(1, crashDirectory);
        TestTickHandle handle = new TestTickHandle(10, () -> {
            throw new IllegalStateException("boom");
        }, false, true);

        attached.runAttached(handle);

        assertEquals(1, handle.recoveries());
        assertFalse(handle.isCancelled());
        assertNull(RegionContext.current());
        try (Stream<Path> files = Files.list(crashDirectory)) {
            assertEquals(1, files.count());
        }
    }

    /** A crash path must not crash: a report that cannot be built must not hide what actually failed. */
    @Test
    void aFailingCrashReportNeverReplacesTheOriginalFailure(@TempDir Path crashDirectory) {
        RegionTickScheduler attached = createScheduler(1, crashDirectory);
        TestTickHandle handle = new TestTickHandle(11, () -> {
            throw new IllegalStateException("boom");
        }, true, false);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> attached.runAttached(handle));

        assertEquals("boom", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertNull(RegionContext.current());
    }

    @Test
    void scheduledHandleTicksRepeatedlyUntilCancelled(@TempDir Path crashDirectory) throws InterruptedException {
        RegionTickScheduler pool = createScheduler(2, crashDirectory);
        pool.start();
        CountDownLatch threeTicks = new CountDownLatch(3);
        AtomicLong ticks = new AtomicLong();
        TestTickHandle handle = new TestTickHandle(1, () -> {
            ticks.incrementAndGet();
            threeTicks.countDown();
        });

        pool.schedule(handle);

        assertTrue(threeTicks.await(3, TimeUnit.SECONDS), "the handle must tick repeatedly on the pool");
        handle.cancel();
        Thread.sleep(150);
        long after = ticks.get();
        Thread.sleep(150);
        assertEquals(after, ticks.get(), "a cancelled handle must stop ticking");
    }

    @Test
    void poolTickFailureInvokesThePolicyAndStopsRescheduling(@TempDir Path crashDirectory) throws InterruptedException {
        CountDownLatch failed = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        scheduler = new RegionTickScheduler(1, false, new LeafsWatchdog(Duration.ofSeconds(60), () -> 0L, message -> { }, stall -> { }), new RegionCrashWriter(crashDirectory, new ModAttribution(_ -> Optional.empty())), (handle, throwable) -> {
            failures.add(throwable);
            failed.countDown();
        });
        scheduler.start();
        AtomicLong attempts = new AtomicLong();
        TestTickHandle handle = new TestTickHandle(1, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        scheduler.schedule(handle);

        assertTrue(failed.await(3, TimeUnit.SECONDS));
        Thread.sleep(150);
        assertEquals(1, attempts.get(), "a failed handle must not be rescheduled");
        assertEquals("boom", failures.peek().getMessage());
    }
}
