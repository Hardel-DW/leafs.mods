package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.AbstractLongSortedSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Unboxed ordered concurrent long set, signed natural order like LongAVLTreeSet. Buckets are
 * striped by {@code key >> groupShift} and hold immutable sorted arrays republished copy-on-write,
 * so a subSet range within one group iterates a single atomic snapshot lock-free; ranges spanning
 * groups merge all bucket snapshots (atomic per bucket, weakly consistent across them).
 */
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
        return rangeIterator(false, 0L, false, 0L);
    }

    @Override
    public LongBidirectionalIterator iterator(long fromElement) {
        long[] merged = collectRange(false, 0L, false, 0L);
        return new SnapshotIterator(ConcurrentOrderedLongSet.this, merged, 0, merged.length, upperBound(merged, fromElement));
    }

    @Override
    public LongSortedSet subSet(long fromElement, long toElement) {
        if (fromElement > toElement) {
            throw new IllegalArgumentException("Start element (" + fromElement + ") is larger than end element (" + toElement + ")");
        }

        return new RangeView(true, fromElement, true, toElement);
    }

    @Override
    public LongSortedSet headSet(long toElement) {
        return new RangeView(false, 0L, true, toElement);
    }

    @Override
    public LongSortedSet tailSet(long fromElement) {
        return new RangeView(true, fromElement, false, 0L);
    }

    @Override
    public long firstLong() {
        return rangeFirst(false, 0L, false, 0L);
    }

    @Override
    public long lastLong() {
        return rangeLast(false, 0L, false, 0L);
    }

    @Override
    public LongComparator comparator() {
        return null;
    }

    private Bucket bucketOf(long value) {
        return buckets[(int) HashCommon.mix(value >> groupShift) & (BUCKET_COUNT - 1)];
    }

    private boolean singleGroup(boolean hasFrom, long from, boolean hasTo, long to) {
        return hasFrom && hasTo && from < to && (from >> groupShift) == ((to - 1) >> groupShift);
    }

    private LongBidirectionalIterator rangeIterator(boolean hasFrom, long from, boolean hasTo, long to) {
        if (hasFrom && hasTo && from >= to) {
            return new SnapshotIterator(this, EMPTY, 0, 0, 0);
        }

        if (singleGroup(hasFrom, from, hasTo, to)) {
            long[] snapshot = bucketOf(from).elements;
            int lower = lowerBound(snapshot, from);
            return new SnapshotIterator(this, snapshot, lower, lowerBound(snapshot, to), lower);
        }

        long[] merged = collectRange(hasFrom, from, hasTo, to);
        return new SnapshotIterator(this, merged, 0, merged.length, 0);
    }

    private int rangeCount(boolean hasFrom, long from, boolean hasTo, long to) {
        if (hasFrom && hasTo && from >= to) {
            return 0;
        }

        if (singleGroup(hasFrom, from, hasTo, to)) {
            long[] snapshot = bucketOf(from).elements;
            return lowerBound(snapshot, to) - lowerBound(snapshot, from);
        }

        int total = 0;
        for (Bucket bucket : buckets) {
            long[] snapshot = bucket.elements;
            total += (hasTo ? lowerBound(snapshot, to) : snapshot.length) - (hasFrom ? lowerBound(snapshot, from) : 0);
        }

        return total;
    }

    private long[] collectRange(boolean hasFrom, long from, boolean hasTo, long to) {
        if (hasFrom && hasTo && from >= to) {
            return EMPTY;
        }

        long[][] snapshots = new long[BUCKET_COUNT][];
        int total = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            long[] snapshot = buckets[i].elements;
            snapshots[i] = snapshot;
            total += (hasTo ? lowerBound(snapshot, to) : snapshot.length) - (hasFrom ? lowerBound(snapshot, from) : 0);
        }

        long[] merged = new long[total];
        int position = 0;
        for (long[] snapshot : snapshots) {
            int lower = hasFrom ? lowerBound(snapshot, from) : 0;
            int upper = hasTo ? lowerBound(snapshot, to) : snapshot.length;
            System.arraycopy(snapshot, lower, merged, position, upper - lower);
            position += upper - lower;
        }

        Arrays.sort(merged);
        return merged;
    }

    private long rangeFirst(boolean hasFrom, long from, boolean hasTo, long to) {
        boolean found = false;
        long first = 0L;
        for (Bucket bucket : buckets) {
            long[] snapshot = bucket.elements;
            int lower = hasFrom ? lowerBound(snapshot, from) : 0;
            int upper = hasTo ? lowerBound(snapshot, to) : snapshot.length;
            if (lower < upper && (!found || snapshot[lower] < first)) {
                first = snapshot[lower];
                found = true;
            }
        }

        if (!found) {
            throw new NoSuchElementException();
        }

        return first;
    }

    private long rangeLast(boolean hasFrom, long from, boolean hasTo, long to) {
        boolean found = false;
        long last = 0L;
        for (Bucket bucket : buckets) {
            long[] snapshot = bucket.elements;
            int lower = hasFrom ? lowerBound(snapshot, from) : 0;
            int upper = hasTo ? lowerBound(snapshot, to) : snapshot.length;
            if (lower < upper && (!found || snapshot[upper - 1] > last)) {
                last = snapshot[upper - 1];
                found = true;
            }
        }

        if (!found) {
            throw new NoSuchElementException();
        }

        return last;
    }

    private static int lowerBound(long[] elements, long key) {
        int index = Arrays.binarySearch(elements, key);
        return index >= 0 ? index : -index - 1;
    }

    private static int upperBound(long[] elements, long key) {
        int index = Arrays.binarySearch(elements, key);
        return index >= 0 ? index + 1 : -index - 1;
    }

    private static final class Bucket {
        volatile long[] elements = EMPTY;
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

    private final class RangeView extends AbstractLongSortedSet {
        private final boolean hasFrom;
        private final long from;
        private final boolean hasTo;
        private final long to;

        RangeView(boolean hasFrom, long from, boolean hasTo, long to) {
            this.hasFrom = hasFrom;
            this.from = from;
            this.hasTo = hasTo;
            this.to = to;
        }

        private boolean inRange(long value) {
            return (!hasFrom || value >= from) && (!hasTo || value < to);
        }

        @Override
        public boolean add(long value) {
            if (!inRange(value)) {
                throw new IllegalArgumentException("Element (" + value + ") out of range");
            }

            return ConcurrentOrderedLongSet.this.add(value);
        }

        @Override
        public boolean remove(long value) {
            return inRange(value) && ConcurrentOrderedLongSet.this.remove(value);
        }

        @Override
        public boolean contains(long value) {
            return inRange(value) && ConcurrentOrderedLongSet.this.contains(value);
        }

        @Override
        public int size() {
            return rangeCount(hasFrom, from, hasTo, to);
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public LongBidirectionalIterator iterator() {
            return rangeIterator(hasFrom, from, hasTo, to);
        }

        @Override
        public LongBidirectionalIterator iterator(long fromElement) {
            long[] merged = collectRange(hasFrom, from, hasTo, to);
            return new SnapshotIterator(ConcurrentOrderedLongSet.this, merged, 0, merged.length, upperBound(merged, fromElement));
        }

        @Override
        public LongSortedSet subSet(long fromElement, long toElement) {
            if (fromElement > toElement) {
                throw new IllegalArgumentException("Start element (" + fromElement + ") is larger than end element (" + toElement + ")");
            }

            if (!inRange(fromElement) && !(hasTo && fromElement == to)) {
                throw new IllegalArgumentException("Start element (" + fromElement + ") out of range");
            }

            if (!inRange(toElement) && !(hasTo && toElement == to)) {
                throw new IllegalArgumentException("End element (" + toElement + ") out of range");
            }

            return new RangeView(true, fromElement, true, toElement);
        }

        @Override
        public LongSortedSet headSet(long toElement) {
            if (!inRange(toElement) && !(hasTo && toElement == to)) {
                throw new IllegalArgumentException("End element (" + toElement + ") out of range");
            }

            return new RangeView(hasFrom, from, true, toElement);
        }

        @Override
        public LongSortedSet tailSet(long fromElement) {
            if (!inRange(fromElement) && !(hasTo && fromElement == to)) {
                throw new IllegalArgumentException("Start element (" + fromElement + ") out of range");
            }

            return new RangeView(true, fromElement, hasTo, to);
        }

        @Override
        public long firstLong() {
            return rangeFirst(hasFrom, from, hasTo, to);
        }

        @Override
        public long lastLong() {
            return rangeLast(hasFrom, from, hasTo, to);
        }

        @Override
        public LongComparator comparator() {
            return null;
        }
    }
}
