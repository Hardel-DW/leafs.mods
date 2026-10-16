package fr.hardel.leafs.network;

import fr.hardel.TestThreads;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketQueueTest {
    private final PlayerPacketQueue queue = new PlayerPacketQueue();
    private final List<String> handled = new ArrayList<>();
    private String level = "initial level";

    @Test
    void drainHandlesInSubmissionOrder() {
        queue.addTask(recording("first"));
        queue.addTask(recording("second"));

        queue.drain(() -> true);

        assertEquals(List.of("first", "second"), handled);
    }

    @Test
    void packetsQueuedDuringTheDrainAreHandledInTheSameDrain() {
        queue.addTask(() -> {
            handled.add("first");
            queue.addTask(recording("late"));
        });

        queue.drain(() -> true);

        assertEquals(List.of("first", "late"), handled);
    }

    @Test
    void handlerFailureEscapesTheDrainAndLeavesTheRestQueued() {
        queue.addTask(() -> {
            throw new IllegalStateException("boom");
        });
        queue.addTask(recording("survivor"));

        assertThrows(IllegalStateException.class, () -> queue.drain(() -> true));

        assertEquals(List.of(), handled);
        assertFalse(queue.handledByCurrentThread(), "the handling scope must not leak past a failing drain");

        queue.drain(() -> true);
        assertEquals(List.of("survivor"), handled);
    }

    @Test
    void ownershipHoldsForEveryPacketOfADrainAcrossAPlayerSwap() {
        List<Boolean> owned = new ArrayList<>();
        queue.addTask(() -> {
            level = "respawned in another level";
            owned.add(queue.handledByCurrentThread());
        });
        queue.addTask(() -> owned.add(queue.handledByCurrentThread()));

        queue.drain(() -> true);

        assertEquals("respawned in another level", level);
        assertEquals(List.of(true, true), owned);
        assertFalse(queue.handledByCurrentThread(), "the scope is thread-local to the drain");
    }

    @Test
    void aDrainStopsWhenOwnershipMovesMidDrain() {
        queue.addTask(() -> {
            handled.add("teleport");
            level = "moved to the nether";
        });
        queue.addTask(recording("stays_queued"));

        queue.drain(() -> level.equals("initial level"));
        assertEquals(List.of("teleport"), handled);

        queue.drain(() -> level.equals("moved to the nether"));
        assertEquals(List.of("teleport", "stays_queued"), handled);
    }

    /** 2026-09-06: a region waiting here for the region holding the player deadlocked with it over a chunk publication. A held player is refused, never waited for. */
    @Test
    void aSecondDrainerIsRefusedWhileTheFirstHandles() throws InterruptedException {
        CountDownLatch insideDrain = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        queue.addTask(() -> {
            insideDrain.countDown();
            TestThreads.await(release);
        });

        Thread regionDrainer = new Thread(() -> queue.drain(() -> true));
        regionDrainer.start();
        TestThreads.await(insideDrain);

        assertFalse(queue.drain(() -> true), "a held player is refused, not waited for");
        assertFalse(queue.handledByCurrentThread(), "a refusal leaves no handling scope behind");

        release.countDown();
        regionDrainer.join();
        assertTrue(queue.drain(() -> true), "a released player is handled again");
    }

    /** 2026-08-31: the player's own pass ran outside this exclusion, so the new owner's drain wrote his movement list while the old owner read it. */
    @Test
    void aPlayerPassKeepsDrainersOutWhileItRuns() throws InterruptedException {
        AtomicBoolean insidePass = new AtomicBoolean();
        AtomicBoolean overlapped = new AtomicBoolean();
        CountDownLatch passStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        queue.addTask(() -> overlapped.compareAndSet(false, insidePass.get()));

        Thread previousOwner = new Thread(() -> queue.handleAs(() -> {
            insidePass.set(true);
            passStarted.countDown();
            TestThreads.await(release);
            insidePass.set(false);
        }));
        previousOwner.start();
        TestThreads.await(passStarted);

        assertFalse(queue.drain(() -> true), "the new owner is refused while the pass runs, the packet stays queued");
        assertEquals(1, queue.pending());

        release.countDown();
        previousOwner.join();
        assertTrue(queue.drain(() -> true));
        assertFalse(overlapped.get(), "no packet may be handled while the player's pass runs");
    }

    @Test
    void concurrentSubmissionsKeepPerThreadOrder() {
        int threads = 4;
        int perThread = 250;
        CountDownLatch done = new CountDownLatch(threads);
        for (int thread = 0; thread < threads; thread++) {
            String prefix = "t%s:".formatted(thread);
            Thread submitter = new Thread(() -> {
                for (int sequence = 0; sequence < perThread; sequence++) {
                    queue.addTask(recording("%s%s".formatted(prefix, sequence)));
                }
                done.countDown();
            });
            submitter.start();
        }
        TestThreads.await(done);

        queue.drain(() -> true);

        assertEquals(threads * perThread, handled.size());
        for (int thread = 0; thread < threads; thread++) {
            String prefix = "t%s:".formatted(thread);
            List<String> sequence = handled.stream().filter(name -> name.startsWith(prefix)).toList();
            for (int index = 0; index < perThread; index++) {
                assertEquals("%s%s".formatted(prefix, index), sequence.get(index), "per-player submissions must stay ordered");
            }
        }
    }

    private Runnable recording(String name) {
        return () -> handled.add(name);
    }
}
