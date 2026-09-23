package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSpliterator;
import it.unimi.dsi.fastutil.longs.LongSpliterators;

import java.util.Collection;
import java.util.function.LongConsumer;

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
    public synchronized boolean removeAll(Collection<?> values) {
        return super.removeAll(values);
    }

    @Override
    public synchronized boolean retainAll(Collection<?> values) {
        return super.retainAll(values);
    }

    @Override
    public synchronized boolean containsAll(Collection<?> values) {
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
    public synchronized Object[] toArray() {
        return super.toArray();
    }

    @Override
    public synchronized <T> T[] toArray(T[] array) {
        return super.toArray(array);
    }

    @Override
    public LongIterator iterator() {
        return LongIterators.asLongIterator(new SnapshotIterator<>(LongArrayList.wrap(snapshot()).iterator(), value -> remove(value.longValue())));
    }

    @Override
    public LongSpliterator spliterator() {
        return LongSpliterators.wrap(snapshot());
    }

    @Override
    public void forEach(LongConsumer action) {
        for (long value : snapshot()) {
            action.accept(value);
        }
    }

    @Override
    public synchronized void ensureCapacity(int capacity) {
        super.ensureCapacity(capacity);
    }

    @Override
    public synchronized boolean trim() {
        return super.trim();
    }

    @Override
    public synchronized boolean trim(int capacity) {
        return super.trim(capacity);
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

    private synchronized long[] snapshot() {
        long[] values = new long[super.size()];
        LongIterator live = super.iterator();
        for (int i = 0; live.hasNext(); i++) {
            values[i] = live.nextLong();
        }

        return values;
    }
}
