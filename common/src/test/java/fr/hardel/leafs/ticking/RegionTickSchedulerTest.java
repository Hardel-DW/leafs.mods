package fr.hardel.leafs.ticking;

import fr.hardel.TestThreads;
import fr.hardel.leafs.network.PacketRouting;
import net.minecraft.CrashReportCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTickSchedulerTest {
    private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    private RegionTickScheduler scheduler;

    @AfterEach
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdown(false, new OwnWork(() -> false));
        }
    }

    private RegionTickScheduler started(TickEpochs epochs, boolean regionThreadNames, BiConsumer<TickHandle, Throwable> failurePolicy) {
        LeafsWatchdog watchdog = new LeafsWatchdog(Duration.ofSeconds(60).toNanos(), () -> 0L, _ -> Map.of(), _ -> { }, _ -> { });
        scheduler = new RegionTickScheduler(Thread.currentThread().getThreadGroup(), epochs, regionThreadNames, watchdog, failurePolicy);
        scheduler.start();
        return scheduler;
    }

    private RegionTickScheduler started() {
        return started(new TickEpochs(1, () -> { }), false, (_, failure) -> failures.add(failure));
    }

    /** The single worker ticks another handle twice: anything it would wrongly tick again had its turn meanwhile. */
    private static void twoTicksOfAnotherHandle(RegionTickScheduler scheduler) {
        CountDownLatch twoTicks = new CountDownLatch(2);
        TestTickHandle other = new TestTickHandle(99, twoTicks::countDown);
        scheduler.schedule(other);
        TestThreads.await(twoTicks);
        other.cancel();
    }

    @Test
    void regionThreadNamesScopeTheWorkerDuringItsTick() {
        started(new TickEpochs(1, () -> { }), true, (_, _) -> { });
        CountDownLatch ticked = new CountDownLatch(1);
        AtomicReference<String> nameDuringTick = new AtomicReference<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        TestTickHandle handle = new TestTickHandle(4, () -> {
            worker.set(Thread.currentThread());
            nameDuringTick.set(Thread.currentThread().getName());
            ticked.countDown();
        });

        scheduler.schedule(handle);
        TestThreads.await(ticked);
        handle.cancel();

        assertEquals("Leafs Server R#4 test:world", nameDuringTick.get());
        TestThreads.until(() -> worker.get().getName().startsWith("Leafs Server Region Worker"));
    }

    /** 2026-08-04 lost GUI packets: only a region tick on a worker batches sends, everyone else keeps vanilla's flush. */
    @Test
    void onlyARegionWorkerSuspendsTheFlush() {
        started();
        CountDownLatch ticked = new CountDownLatch(1);
        AtomicBoolean flushOnWorker = new AtomicBoolean(true);
        TestTickHandle handle = new TestTickHandle(1, () -> {
            flushOnWorker.set(PacketRouting.scopedFlush(true));
            ticked.countDown();
        });

        scheduler.schedule(handle);
        TestThreads.await(ticked);
        handle.cancel();

        assertFalse(flushOnWorker.get());
        assertTrue(PacketRouting.scopedFlush(true));
        assertFalse(PacketRouting.scopedFlush(false));
    }

    @Test
    void aNormalShutdownLetsATickFinishTheWaitTheServerServes() {
        started();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        scheduler.schedule(new TestTickHandle(1, () -> {
            waiting.countDown();
            new OwnWork(() -> false).until(delivered::get, OwnWork.NO_HELP);
            finished.set(true);
        }));

        TestThreads.await(waiting);
        scheduler.shutdown(false, new OwnWork(() -> !delivered.getAndSet(true)));

        assertTrue(finished.get(), "the shutdown must return after the tick ends, before the saves");
        assertTrue(failures.isEmpty());
    }

    /** 2026-09-23: a Leafs wait ignored the interrupt, so a worker waiting on a failed chunk blocked the server stop forever. */
    @Test
    void aCrashShutdownEndsATickStuckInAWait() {
        started();
        CountDownLatch waiting = new CountDownLatch(1);
        scheduler.schedule(new TestTickHandle(1, () -> {
            waiting.countDown();
            new OwnWork(() -> false).until(() -> false, OwnWork.NO_HELP);
        }));

        TestThreads.await(waiting);
        scheduler.shutdown(true, new OwnWork(() -> false));

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
    void aHandleTicksUntilItIsCancelled() {
        started();
        CountDownLatch threeTicks = new CountDownLatch(3);
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<TestTickHandle> self = new AtomicReference<>();
        self.set(new TestTickHandle(1, () -> {
            if (ticks.incrementAndGet() == 3) {
                self.get().cancel();
            }

            threeTicks.countDown();
        }));

        scheduler.schedule(self.get());
        TestThreads.await(threeTicks);
        twoTicksOfAnotherHandle(scheduler);

        assertEquals(3, ticks.get(), "a cancelled handle must stop ticking");
    }

    /** 2026-09-23: a start missed behind the server thread retried one period later, in phase with the next server tick, so the region never ticked again. */
    @Test
    void aMissedStartWaitsTheServerTickEndThenRuns() {
        started();
        AtomicBoolean held = new AtomicBoolean(true);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch ticked = new CountDownLatch(1);
        TestTickHandle handle = new TestTickHandle(1, () -> {
            attempts.incrementAndGet();
            return !held.get();
        }, ticked::countDown);

        scheduler.schedule(handle);
        TestThreads.until(() -> handle.stages().missedStarts() == 1);
        twoTicksOfAnotherHandle(scheduler);
        assertEquals(1, attempts.get(), "a held region retried before the server tick ended");

        held.set(false);
        TestThreads.until(() -> {
            scheduler.wakeMissed();
            return ticked.getCount() == 0;
        });
        handle.cancel();

        assertEquals(1, handle.stages().missedStarts());
    }

    @Test
    void aFailedTickGoesThroughThePolicyAndIsNeverRescheduled() {
        CountDownLatch failed = new CountDownLatch(1);
        started(new TickEpochs(1, () -> { }), false, (_, failure) -> {
            failures.add(failure);
            failed.countDown();
        });
        AtomicInteger attempts = new AtomicInteger();

        scheduler.schedule(new TestTickHandle(1, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        }));
        TestThreads.await(failed);
        twoTicksOfAnotherHandle(scheduler);

        assertEquals(1, attempts.get(), "a failed handle must not be rescheduled");
        assertEquals("boom", failures.peek().getMessage());
    }

    /** 2026-09-28: a tick-end drain that threw escaped the worker loop through its finally, and the worker died without the failure policy. */
    @Test
    void aTickEndDrainThatThrowsGoesThroughTheFailurePolicyAndTheWorkerLivesOn() {
        CountDownLatch failed = new CountDownLatch(1);
        AtomicBoolean thrown = new AtomicBoolean();
        TickEpochs epochs = new TickEpochs(1, () -> {
            if (!thrown.getAndSet(true)) {
                throw new IllegalStateException("drain");
            }
        });
        started(epochs, false, (_, failure) -> {
            if (failure.getMessage().equals("drain")) {
                failed.countDown();
            }
        });

        scheduler.schedule(new TestTickHandle(1, () -> { }));
        TestThreads.await(failed);

        twoTicksOfAnotherHandle(scheduler);
    }
}
