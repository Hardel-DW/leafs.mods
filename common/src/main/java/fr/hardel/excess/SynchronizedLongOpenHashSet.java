package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Collection;

/** A LongOpenHashSet other threads may write while one iterates: writes take the set's lock, iteration walks a snapshot that cannot remove. Fits a field declared LongOpenHashSet. */
public final class SynchronizedLongOpenHashSet extends LongOpenHashSet {

    @Override
    public synchronized boolean add(long value) {
        return super.add(value);
    }

    @Override
    public synchronized boolean remove(long value) {
        return super.remove(value);
    }

    @Override
    public synchronized boolean contains(long value) {
        return super.contains(value);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized boolean addAll(LongCollection values) {
        return super.addAll(values);
    }

    @Override
    public synchronized boolean addAll(Collection<? extends Long> values) {
        return super.addAll(values);
    }

    @Override
    public synchronized boolean removeAll(LongCollection values) {
        return super.removeAll(values);
    }

    @Override
    public synchronized boolean retainAll(LongCollection values) {
        return super.retainAll(values);
    }

    @Override
    public synchronized boolean containsAll(LongCollection values) {
        return super.containsAll(values);
    }

    @Override
    public long[] toLongArray() {
        return snapshot();
    }

    @Override
    public synchronized long[] toArray(long[] array) {
        return super.toArray(array);
    }

    @Override
    public LongIterator iterator() {
        return new ArrayLongIterator(snapshot());
    }

    @Override
    public synchronized boolean trim() {
        return super.trim();
    }

    @Override
    public synchronized boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public synchronized int hashCode() {
        return super.hashCode();
    }

    @Override
    public synchronized String toString() {
        return super.toString();
    }

    @Override
    public synchronized LongOpenHashSet clone() {
        return super.clone();
    }

    /** Copied from the parent's own iterator: the parent's array conversions call iterator(), which is this snapshot. */
    private synchronized long[] snapshot() {
        long[] values = new long[super.size()];
        LongIterator live = super.iterator();
        for (int i = 0; live.hasNext(); i++) {
            values[i] = live.nextLong();
        }

        return values;
    }

    private static final class ArrayLongIterator implements LongIterator {
        private final long[] values;
        private int next;

        private ArrayLongIterator(long[] values) {
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            return next < values.length;
        }

        @Override
        public long nextLong() {
            return values[next++];
        }
    }
}
