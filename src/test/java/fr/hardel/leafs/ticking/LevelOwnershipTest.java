package fr.hardel.leafs.ticking;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelOwnershipTest {

    @Test
    void levelSerialIsReentrant() {
        LevelOwnership ownership = new LevelOwnership();
        ownership.enterLevelSerial();
        assertTrue(ownership.tryEnterLevelSerial());
        assertTrue(ownership.isLevelSerialHeldByCurrentThread());
        ownership.exitLevelSerial();
        ownership.exitLevelSerial();
        assertFalse(ownership.isLevelSerialHeldByCurrentThread());
    }

    @Test
    void aRegionTickBlocksTheSerialSideAndBack() {
        LevelOwnership ownership = new LevelOwnership();
        assertTrue(ownership.tryEnterRegionTick());
        assertFalse(ownership.tryEnterLevelSerial());
        ownership.exitRegionTick();

        assertTrue(ownership.tryEnterLevelSerial());
        assertFalse(ownership.tryEnterRegionTick());
        ownership.exitLevelSerial();
        assertTrue(ownership.tryEnterRegionTick());
        ownership.exitRegionTick();
    }

    @Test
    void serialAcquisitionWaitsOutAnInFlightRegionTick() throws InterruptedException {
        LevelOwnership ownership = new LevelOwnership();
        CountDownLatch regionEntered = new CountDownLatch(1);
        CountDownLatch releaseRegion = new CountDownLatch(1);
        Thread region = new Thread(() -> {
            assertTrue(ownership.tryEnterRegionTick());
            regionEntered.countDown();
            try {
                assertTrue(releaseRegion.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                ownership.exitRegionTick();
            }
        });
        region.start();
        assertTrue(regionEntered.await(5, TimeUnit.SECONDS));

        assertFalse(ownership.tryEnterLevelSerial());
        releaseRegion.countDown();
        ownership.enterLevelSerial();
        ownership.exitLevelSerial();
        region.join(5_000);
        assertFalse(region.isAlive());
    }

    /** 11 août 2026: two region workers share the read side, so player roster mutations need the serialized variant. */
    @Test
    void exclusiveSerializedNeverInterleavesTwoReadHolders() throws InterruptedException {
        LevelOwnership ownership = new LevelOwnership();
        int workers = 4;
        int increments = 500;
        int[] unguardedCounter = new int[1];
        CountDownLatch done = new CountDownLatch(workers);
        for (int i = 0; i < workers; i++) {
            new Thread(() -> {
                assertTrue(ownership.tryEnterRegionTick());
                try {
                    for (int step = 0; step < increments; step++) {
                        ownership.runExclusiveSerialized(() -> unguardedCounter[0]++);
                    }
                } finally {
                    ownership.exitRegionTick();
                    done.countDown();
                }
            }).start();
        }

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(workers * increments == unguardedCounter[0], "read holders interleaved a serialized mutation");
    }

    @Test
    void exclusiveSerializedTakesTheExclusionForForeignThreads() {
        LevelOwnership ownership = new LevelOwnership();
        boolean[] ranExclusive = new boolean[1];
        ownership.runExclusiveSerialized(() -> ranExclusive[0] = ownership.isLevelSerialHeldByCurrentThread());
        assertTrue(ranExclusive[0]);
        assertTrue(ownership.tryEnterRegionTick(), "the exclusion must be released afterwards");
        ownership.exitRegionTick();
    }

    @Test
    void aQueuedSerialTakerStopsNewRegionTicks() throws InterruptedException {
        LevelOwnership ownership = new LevelOwnership();
        assertTrue(ownership.tryEnterRegionTick());

        Thread serial = new Thread(() -> {
            ownership.enterLevelSerial();
            ownership.exitLevelSerial();
        });
        serial.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean yielded = false;
        while (System.nanoTime() < deadline) {
            if (!ownership.tryEnterRegionTick()) {
                yielded = true;
                break;
            }

            ownership.exitRegionTick();
        }
        assertTrue(yielded, "a parked serial taker must stop new region ticks");

        ownership.exitRegionTick();
        serial.join(5_000);
        assertFalse(serial.isAlive());
    }
}
