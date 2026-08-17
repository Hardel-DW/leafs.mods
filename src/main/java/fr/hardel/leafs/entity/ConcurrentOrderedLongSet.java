package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.AbstractLongSortedSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.Arrays;
import java.util.NoSuchElementException;

/** Striped by {@code key >> groupShift}, copy-on-write sorted arrays per bucket: a single-group range reads one lock-free snapshot. */
public final class ConcurrentOrderedLongSet extends AbstractLongSortedSet {
    private static final long[] EMPTY = new long[0];
    private static final int BUCKET_COUNT = 128;

    private final int groupShift;
    private final Bucket[] buckets;

    public ConcurrentOrderedLongSet(int groupShift) {
        if (groupShift < 1 || groupShift > 63) {
            throw new IllegalArgumentException("groupShift out of range: " + groupShift);
        }

        this.groupShift = groupShift;
        this.buckets = new Bucket[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            buckets[i] = new Bucket();
        }
    }

    @Override
    public boolean add(long value) {
        Bucket bucket = bucketOf(value);
        synchronized (bucket) {
            long[] current = bucket.elements;
            int index = Arrays.binarySearch(current, value);
            if (index >= 0) {
                return false;
            }

            int insertion = -index - 1;
            long[] grown = new long[current.length + 1];
            System.arraycopy(current, 0, grown, 0, insertion);
            grown[insertion] = value;
            System.arraycopy(current, insertion, grown, insertion + 1, current.length - insertion);
            bucket.elements = grown;
            return true;
        }
    }

    @Override
    public boolean remove(long value) {
        Bucket bucket = bucketOf(value);
        synchronized (bucket) {
            long[] current = bucket.elements;
            int index = Arrays.binarySearch(current, value);
            if (index < 0) {
                return false;
            }

            long[] shrunk = current.length == 1 ? EMPTY : new long[current.length - 1];
            System.arraycopy(current, 0, shrunk, 0, index);
            System.arraycopy(current, index + 1, shrunk, index, current.length - index - 1);
            bucket.elements = shrunk;
            return true;
        }
    }

    @Override
    public boolean contains(long value) {
        return Arrays.binarySearch(bucketOf(value).elements, value) >= 0;
    }

    @Override
    public int size() {
        int total = 0;
        for (Bucket bucket : buckets) {
            total += bucket.elements.length;
        }

        return total;
    }

    @Override
    public boolean isEmpty() {
        for (Bucket bucket : buckets) {
            if (bucket.elements.length > 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void clear() {
        for (Bucket bucket : buckets) {
            synchronized (bucket) {
                bucket.elements = EMPTY;
            }
        }
    }

    @Override
    public LongBidirectionalIterator iterator() {
        return snapshot(false, 0L, false, 0L).iterator();
    }

    @Override
    public LongBidirectionalIterator iterator(long fromElement) {
        return snapshot(false, 0L, false, 0L).iterator(fromElement);
    }

    @Override
    public LongSortedSet subSet(long fromElement, long toElement) {
        if (fromElement > toElement) {
            throw new IllegalArgumentException("Start element (" + fromElement + ") is larger than end element (" + toElement + ")");
        }

        return snapshot(true, fromElement, true, toElement);
    }

    @Override
    public LongSortedSet headSet(long toElement) {
        return snapshot(false, 0L, true, toElement);
    }

    @Override
    public LongSortedSet tailSet(long fromElement) {
        return snapshot(true, fromElement, false, 0L);
    }

    @Override
    public long firstLong() {
        return snapshot(false, 0L, false, 0L).firstLong();
    }

    @Override
    public long lastLong() {
        return snapshot(false, 0L, false, 0L).lastLong();
    }

    @Override
    public LongComparator comparator() {
        return null;
    }

    private Bucket bucketOf(long value) {
        return buckets[(int) HashCommon.mix(value >> groupShift) & (BUCKET_COUNT - 1)];
    }

    /** One bucket's live array when the range cannot span two groups, a merged copy of every bucket's slice otherwise. */
    private Snapshot snapshot(boolean hasFrom, long from, boolean hasTo, long to) {
        if (hasFrom && hasTo && from >= to) {
            return new Snapshot(this, EMPTY, 0, 0);
        }

        if (hasFrom && hasTo && (from >> groupShift) == ((to - 1) >> groupShift)) {
            long[] elements = bucketOf(from).elements;
            return new Snapshot(this, elements, lowerBound(elements, from), lowerBound(elements, to));
        }

        long[][] slices = new long[BUCKET_COUNT][];
        int total = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            long[] elements = buckets[i].elements;
            slices[i] = elements;
            total += (hasTo ? lowerBound(elements, to) : elements.length) - (hasFrom ? lowerBound(elements, from) : 0);
        }

        long[] merged = new long[total];
        int position = 0;
        for (long[] elements : slices) {
            int lower = hasFrom ? lowerBound(elements, from) : 0;
            int upper = hasTo ? lowerBound(elements, to) : elements.length;
            System.arraycopy(elements, lower, merged, position, upper - lower);
            position += upper - lower;
        }

        Arrays.sort(merged);
        return new Snapshot(this, merged, 0, merged.length);
    }

    private static int lowerBound(long[] elements, long key) {
        return lowerBound(elements, 0, elements.length, key);
    }

    private static int lowerBound(long[] elements, int from, int to, long key) {
        int index = Arrays.binarySearch(elements, from, to, key);
        return index >= 0 ? index : -index - 1;
    }

    private static final class Bucket {
        volatile long[] elements = EMPTY;
    }

    /** An immutable window over a captured array: the sub-views vanilla asks for are read-only scans. */
    private static final class Snapshot extends AbstractLongSortedSet {
        private final ConcurrentOrderedLongSet owner;
        private final long[] elements;
        private final int lower;
        private final int upper;

        Snapshot(ConcurrentOrderedLongSet owner, long[] elements, int lower, int upper) {
            this.owner = owner;
            this.elements = elements;
            this.lower = lower;
            this.upper = upper;
        }

        @Override
        public boolean contains(long value) {
            return Arrays.binarySearch(elements, lower, upper, value) >= 0;
        }

        @Override
        public int size() {
            return upper - lower;
        }

        @Override
        public boolean isEmpty() {
            return lower == upper;
        }

        @Override
        public LongBidirectionalIterator iterator() {
            return new SnapshotIterator(owner, elements, lower, upper, lower);
        }

        @Override
        public LongBidirectionalIterator iterator(long fromElement) {
            int index = Arrays.binarySearch(elements, lower, upper, fromElement);
            return new SnapshotIterator(owner, elements, lower, upper, index >= 0 ? index + 1 : -index - 1);
        }

        @Override
        public LongSortedSet subSet(long fromElement, long toElement) {
            if (fromElement > toElement) {
                throw new IllegalArgumentException("Start element (" + fromElement + ") is larger than end element (" + toElement + ")");
            }

            return new Snapshot(owner, elements, bound(fromElement), bound(toElement));
        }

        @Override
        public LongSortedSet headSet(long toElement) {
            return new Snapshot(owner, elements, lower, bound(toElement));
        }

        @Override
        public LongSortedSet tailSet(long fromElement) {
            return new Snapshot(owner, elements, bound(fromElement), upper);
        }

        @Override
        public long firstLong() {
            if (lower == upper) {
                throw new NoSuchElementException();
            }

            return elements[lower];
        }

        @Override
        public long lastLong() {
            if (lower == upper) {
                throw new NoSuchElementException();
            }

            return elements[upper - 1];
        }

        @Override
        public LongComparator comparator() {
            return null;
        }

        private int bound(long key) {
            return lowerBound(elements, lower, upper, key);
        }
    }

    private static final class SnapshotIterator implements LongBidirectionalIterator {
        private final ConcurrentOrderedLongSet owner;
        private final long[] elements;
        private final int lower;
        private final int upper;
        private int cursor;
        private boolean hasLastReturned;
        private long lastReturned;

        SnapshotIterator(ConcurrentOrderedLongSet owner, long[] elements, int lower, int upper, int cursor) {
            this.owner = owner;
            this.elements = elements;
            this.lower = lower;
            this.upper = upper;
            this.cursor = cursor;
        }

        @Override
        public boolean hasNext() {
            return cursor < upper;
        }

        @Override
        public long nextLong() {
            if (cursor >= upper) {
                throw new NoSuchElementException();
            }

            lastReturned = elements[cursor++];
            hasLastReturned = true;

            return lastReturned;
        }

        @Override
        public boolean hasPrevious() {
            return cursor > lower;
        }

        @Override
        public long previousLong() {
            if (cursor <= lower) {
                throw new NoSuchElementException();
            }

            lastReturned = elements[--cursor];
            hasLastReturned = true;

            return lastReturned;
        }

        /** Removes from the live set, not the snapshot: the concurrent-iteration contract callers already have. */
        @Override
        public void remove() {
            if (!hasLastReturned) {
                throw new IllegalStateException("No element to remove");
            }

            owner.remove(lastReturned);
            hasLastReturned = false;
        }
    }
}
