package fr.hardel.leafs.thread;

import fr.hardel.TestThreads;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialEventsTest {

    /** 2026-10-16: two chunk workers unloading at once corrupted the lantern index of NeoVitae in its ChunkEvent.Unload handler. */
    @Test
    void twoDeliveriesNeverOverlap() throws InterruptedException {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicBoolean secondEntered = new AtomicBoolean();
        Thread first = new Thread(() -> SerialEvents.deliver(() -> {
            inside.countDown();
            TestThreads.await(gate);
            return null;
        }));
        first.start();
        TestThreads.await(inside);
        Thread second = new Thread(() -> SerialEvents.deliver(() -> {
            secondEntered.set(true);
            return null;
        }));
        second.start();
        TestThreads.awaitParked(second);

        assertFalse(secondEntered.get(), "the second handler ran during the first");
        gate.countDown();
        first.join();
        second.join();
        assertTrue(secondEntered.get());
    }

    /** A handler that loads another chunk waits for it, and that chunk delivers its own load event meanwhile. */
    @Test
    void aWaitingHandlerGivesTheDeliveryBack() throws InterruptedException {
        CountDownLatch nestedDelivered = new CountDownLatch(1);
        Thread nested = new Thread(() -> SerialEvents.deliver(() -> {
            nestedDelivered.countDown();
            return null;
        }));
        Thread handler = new Thread(() -> SerialEvents.deliver(() -> {
            nested.start();
            SerialEvents.released(() -> TestThreads.await(nestedDelivered));
            return null;
        }));

        handler.start();
        handler.join();
        nested.join();
        assertEquals(0, nestedDelivered.getCount());
    }
}
