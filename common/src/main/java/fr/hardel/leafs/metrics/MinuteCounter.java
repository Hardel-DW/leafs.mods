package fr.hardel.leafs.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.LongSupplier;

/** Sliding one-minute counter plus lifetime total, any thread. Sixty buckets recycle in place; a bucket reset can lose one count, harmless for a debug metric. */
public final class MinuteCounter {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private final AtomicLongArray counts = new AtomicLongArray(60);
    private final AtomicLongArray seconds = new AtomicLongArray(60);
    private final AtomicLong total = new AtomicLong();
    private final LongSupplier secondSource;

    public MinuteCounter() {
        this(() -> System.nanoTime() / NANOS_PER_SECOND);
    }

    MinuteCounter(LongSupplier secondSource) {
        this.secondSource = secondSource;
    }

    public void increment() {
        long second = secondSource.getAsLong();
        int slot = Math.floorMod(second, 60);
        if (seconds.get(slot) != second) {
            seconds.set(slot, second);
            counts.set(slot, 0);
        }

        counts.incrementAndGet(slot);
        total.incrementAndGet();
    }

    /** Cumulative, for a sampler that differences two reads. */
    public long total() {
        return total.get();
    }

    public long perMinute() {
        long now = secondSource.getAsLong();
        long sum = 0;
        for (int slot = 0; slot < 60; slot++) {
            long second = seconds.get(slot);
            if (second > now - 60 && second <= now) {
                sum += counts.get(slot);
            }
        }

        return sum;
    }
}
