package fr.hardel.leafs.ticking;

import fr.hardel.leafs.network.PacketRouting;
import java.util.Map;
import net.minecraft.CrashReportCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTickSchedulerTest {
    private static final long TICK_PERIOD_NANOS = 50_000_000L;

    private RegionTickScheduler scheduler;

    @AfterEach
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdown(false, new OwnWork(() -> false));
        }
    }

    private RegionTickScheduler createScheduler(int threads) {
        scheduler = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), threads, () -> TICK_PERIOD_NANOS, false, new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), message -> { }, stall -> { }), (handle, throwable) -> { });
        return scheduler;
    }

    @Test
    void regionThreadNamesScopeTheWorkerDuringItsTick() throws InterruptedException {
        scheduler = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), 1, () -> TICK_PERIOD_NANOS, true, new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), message -> { }, stall -> { }), (handle, throwable) -> { });
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
        assertEquals("Leafs Server R#4 test:world", nameDuringTick.get());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!worker.get().getName().startsWith("Leafs Server Region Worker") && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }

        assertTrue(worker.get().getName().startsWith("Leafs Server Region Worker"));
    }

    /** 2026-08-04 lost GUI packets: only a region tick on a worker batches sends, everyone else keeps vanilla's flush. */
    @Test
    void onlyARegionWorkerSuspendsTheFlush() throws InterruptedException {
        RegionTickScheduler scheduler = createScheduler(1);
        scheduler.start();
        CountDownLatch ticked = new CountDownLatch(1);
        AtomicBoolean flushOnWorker = new AtomicBoolean(true);
        TestTickHandle handle = new TestTickHandle(1, () -> {
            flushOnWorker.set(PacketRouting.scopedFlush(true));
            ticked.countDown();
        });

        scheduler.schedule(handle);

        assertTrue(ticked.await(5, TimeUnit.SECONDS));
        handle.cancel();
        assertFalse(flushOnWorker.get());
        assertTrue(PacketRouting.scopedFlush(true));
        assertFalse(PacketRouting.scopedFlush(false));
    }

    @Test
    void shutdownWaitsForATickInFlight() throws InterruptedException {
        RegionTickScheduler scheduler = createScheduler(1);
        scheduler.start();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean ticking = new AtomicBoolean();
        
        scheduler.schedule(new TestTickHandle(1, () -> {
            ticking.set(true);
            started.countDown();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
            ticking.set(false);
        }));

        assertTrue(started.await(5, TimeUnit.SECONDS));
        scheduler.shutdown(false, new OwnWork(() -> false));
        assertFalse(ticking.get());
    }

    @Test
    void aNormalShutdownLetsATickFinishTheWaitTheServerServes() throws InterruptedException {
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        RegionTickScheduler stopping = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), 1, () -> TICK_PERIOD_NANOS, false, new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), message -> { }, stall -> { }), (_, failure) -> failures.add(failure));
        stopping.start();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        stopping.schedule(new TestTickHandle(1, () -> {
            waiting.countDown();
            new OwnWork(() -> false).until(delivered::get);
            finished.set(true);
        }));

        assertTrue(waiting.await(5, TimeUnit.SECONDS));
        stopping.shutdown(false, new OwnWork(() -> !delivered.getAndSet(true)));

        assertTrue(finished.get(), "the shutdown must return after the tick ends, before the saves");
        assertTrue(failures.isEmpty());
    }

    /** 2026-09-23: a Leafs wait ignored the interrupt, so a worker waiting on a failed chunk blocked the server stop forever. */
    @Test
    void aCrashShutdownEndsATickStuckInAWait() throws InterruptedException {
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        RegionTickScheduler stopping = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), 1, () -> TICK_PERIOD_NANOS, false, new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), message -> { }, stall -> { }), (_, failure) -> failures.add(failure));
        stopping.start();
        CountDownLatch waiting = new CountDownLatch(1);
        stopping.schedule(new TestTickHandle(1, () -> {
            waiting.countDown();
            new OwnWork(() -> false).until(() -> false);
        }));

        assertTrue(waiting.await(5, TimeUnit.SECONDS));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> stopping.shutdown(true, new OwnWork(() -> false)), "a crash stop must interrupt the wait");
        assertTrue(failures.isEmpty(), "a tick cut short by the shutdown is not a crash");
    }

    @Test
    void aCrashCategoryNamesTheRegion() {
        TestTickHandle handle = new TestTickHandle(9, () -> { });
        handle.tick();
        CrashReportCategory category = new CrashReportCategory("Leafs region");
        StringBuilder details = new StringBuilder();

        handle.fillCrashReportCategory(category);
        category.getDetails(details);

        assertTrue(details.toString().contains("Id: 9"));
        assertTrue(details.toString().contains("Dimension: test:world"));
        assertTrue(details.toString().contains("Tick: 1"));
    }

    @Test
    void scheduledHandleTicksRepeatedlyUntilCancelled() throws InterruptedException {
        RegionTickScheduler pool = createScheduler(2);
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

    /** 2026-09-23: a start missed behind the server thread retried one period later, in phase with the next server tick, so the region never ticked again. */
    @Test
    void aMissedStartWaitsTheServerTickEndThenRuns() throws InterruptedException {
        RegionTickScheduler pool = createScheduler(1);
        pool.start();
        AtomicBoolean held = new AtomicBoolean(true);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstAttempt = new CountDownLatch(1);
        CountDownLatch ticked = new CountDownLatch(1);
        TestTickHandle handle = new TestTickHandle(1, () -> {
            attempts.incrementAndGet();
            firstAttempt.countDown();
            return !held.get();
        }, ticked::countDown);

        pool.schedule(handle);

        assertTrue(firstAttempt.await(3, TimeUnit.SECONDS));
        Thread.sleep(300);
        assertEquals(1, attempts.get(), "a held region retried before the server tick ended");
        held.set(false);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!ticked.await(50, TimeUnit.MILLISECONDS) && System.nanoTime() < deadline) {
            pool.wakeMissed();
        }

        handle.cancel();
        assertEquals(0, ticked.getCount(), "the missed start never ran after the server tick ended");
        assertEquals(1, handle.stages().missedStarts());
    }

    @Test
    void poolTickFailureInvokesThePolicyAndStopsRescheduling() throws InterruptedException {
        CountDownLatch failed = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        scheduler = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), 1, () -> TICK_PERIOD_NANOS, false, new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), message -> { }, stall -> { }), (handle, throwable) -> {
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
