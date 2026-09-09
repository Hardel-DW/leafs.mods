package fr.hardel.leafs.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinuteCounterTest {

    private final AtomicLong second = new AtomicLong();
    private final MinuteCounter counter = new MinuteCounter(second::get);

    @Test
    void countsInsideTheMinuteAndForgetsBeyondIt() {
        counter.increment();
        counter.increment();
        second.set(30);
        counter.increment();

        assertEquals(3, counter.perMinute());

        second.set(61);
        assertEquals(1, counter.perMinute(), "the two counts of second 0 are older than a minute");

        second.set(200);
        assertEquals(0, counter.perMinute());
    }

    @Test
    void aRecycledBucketDropsItsStaleCount() {
        counter.increment();
        second.set(60);
        counter.increment();

        assertEquals(1, counter.perMinute(), "second 60 reuses the slot of second 0 and resets it");
    }
}
