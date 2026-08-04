package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentOrderedLongSetTest {
    private static final int SECTION_X_SHIFT = 42;

    private final ConcurrentOrderedLongSet set = new ConcurrentOrderedLongSet(SECTION_X_SHIFT);

    /** SectionPos.asLong: x in the top 22 bits, z in the middle 22, y in the low 20. */
    private static long sectionKey(int x, int y, int z) {
        return (x & 0x3FFFFFL) << 42 | (y & 0xFFFFFL) | (z & 0x3FFFFFL) << 20;
    }

    private static List<Long> drain(LongIterator iterator) {
        List<Long> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.nextLong());
        }

        return values;
    }

    @Test
    void addAndDuplicateAdd() {
        assertTrue(set.add(5L));
        assertFalse(set.add(5L));
        assertTrue(set.contains(5L));
        assertEquals(1, set.size());
    }

    @Test
    void removeAndAbsentRemove() {
        set.add(5L);

        assertTrue(set.remove(5L));
        assertFalse(set.remove(5L));
        assertFalse(set.contains(5L));
        assertTrue(set.isEmpty());
    }

    @Test
    void iterationIsSortedSignedOrder() {
        TreeSet<Long> reference = new TreeSet<>();
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            long key = sectionKey(random.nextInt(200) - 100, random.nextInt(24) - 4, random.nextInt(200) - 100);
            set.add(key);
            reference.add(key);
        }

        assertEquals(new ArrayList<>(reference), drain(set.iterator()));
        assertEquals(reference.first(), set.firstLong());
        assertEquals(reference.last(), set.lastLong());
    }

    @Test
    void vanillaAabbSliceQueryMatchesReference() {
        TreeSet<Long> reference = new TreeSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -2; y <= 2; y++) {
                    long key = sectionKey(x, y, z);
                    set.add(key);
                    reference.add(key);
                }
            }
        }

        for (int x = -3; x <= 3; x++) {
            long lowest = sectionKey(x, 0, 0);
            long highest = sectionKey(x, -1, -1);
            assertEquals(
                    new ArrayList<>(reference.subSet(lowest, highest + 1L)),
                    drain(set.subSet(lowest, highest + 1L).iterator()));
        }
    }

    @Test
    void vanillaChunkColumnQueryMatchesReference() {
        TreeSet<Long> reference = new TreeSet<>();
        for (int y = -4; y <= 19; y++) {
            for (int x : new int[] {7, 8}) {
                for (int z : new int[] {-9, 4}) {
                    long key = sectionKey(x, y, z);
                    set.add(key);
                    reference.add(key);
                }
            }
        }

        long lowest = sectionKey(7, 0, -9);
        long highest = sectionKey(7, -1, -9);
        LongSortedSet column = set.subSet(lowest, highest + 1L);
        assertEquals(new ArrayList<>(reference.subSet(lowest, highest + 1L)), drain(column.iterator()));
        assertEquals(24, column.size());
        assertFalse(column.isEmpty());
    }

    @Test
    void crossGroupSubSetMatchesReference() {
        TreeSet<Long> reference = new TreeSet<>();
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            long key = sectionKey(random.nextInt(40) - 20, random.nextInt(10), random.nextInt(40) - 20);
            set.add(key);
            reference.add(key);
        }

        long from = sectionKey(-10, 0, 0);
        long to = sectionKey(11, 0, 0);
        assertEquals(new ArrayList<>(reference.subSet(from, to)), drain(set.subSet(from, to).iterator()));
        assertEquals(reference.subSet(from, to).size(), set.subSet(from, to).size());
    }

    @Test
    void emptyRangeAndEmptySet() {
        set.add(10L);

        assertFalse(set.subSet(20L, 20L).iterator().hasNext());
        assertTrue(set.subSet(20L, 30L).isEmpty());
        assertEquals(0, set.subSet(20L, 30L).size());
        assertThrows(NoSuchElementException.class, () -> new ConcurrentOrderedLongSet(SECTION_X_SHIFT).firstLong());
    }

    @Test
    void subSetRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> set.subSet(10L, 5L));
    }

    @Test
    void viewAddRejectsOutOfRangeAndMutatesParent() {
        LongSortedSet view = set.subSet(0L, 100L);

        assertThrows(IllegalArgumentException.class, () -> view.add(100L));
        assertTrue(view.add(50L));
        assertTrue(set.contains(50L));
        assertTrue(view.remove(50L));
        assertFalse(set.contains(50L));
    }

    @Test
    void headAndTailSets() {
        for (long v = 0; v < 10; v++) {
            set.add(v * 3);
        }

        assertEquals(List.of(0L, 3L, 6L), drain(set.headSet(9L).iterator()));
        assertEquals(List.of(9L, 12L, 15L, 18L, 21L, 24L, 27L), drain(set.tailSet(9L).iterator()));
        assertEquals(9L, set.tailSet(9L).firstLong());
        assertEquals(6L, set.headSet(9L).lastLong());
    }

    @Test
    void iteratorFromElementPositionsAroundIt() {
        set.add(10L);
        set.add(20L);
        set.add(30L);

        LongBidirectionalIterator iterator = set.iterator(20L);
        assertEquals(30L, iterator.nextLong());
        iterator.previousLong();
        assertEquals(20L, iterator.previousLong());
        assertEquals(10L, iterator.previousLong());
        assertFalse(iterator.hasPrevious());
    }

    @Test
    void bidirectionalIterationOverRange() {
        for (long v = 0; v < 5; v++) {
            set.add(v);
        }

        LongBidirectionalIterator iterator = set.subSet(1L, 4L).iterator();
        assertEquals(1L, iterator.nextLong());
        assertEquals(2L, iterator.nextLong());
        assertEquals(2L, iterator.previousLong());
        assertEquals(1L, iterator.previousLong());
        assertFalse(iterator.hasPrevious());
    }

    @Test
    void negativeCoordinatesKeepSignedOrderAgainstAvlReference() {
        TreeSet<Long> reference = new TreeSet<>();
        for (int x : new int[] {-2, -1, 0, 1}) {
            for (int y : new int[] {-4, 0, 5}) {
                long key = sectionKey(x, y, x);
                set.add(key);
                reference.add(key);
            }
        }

        assertEquals(new ArrayList<>(reference), drain(set.iterator()));
    }

    @Test
    void mutationDuringIterationKeepsTheSnapshot() {
        long base = sectionKey(4, 0, 0);
        for (int y = 0; y < 8; y++) {
            set.add(base + y);
        }

        LongIterator iterator = set.subSet(base, base + 8).iterator();
        List<Long> seen = new ArrayList<>();
        while (iterator.hasNext()) {
            seen.add(iterator.nextLong());
            set.remove(base + 7);
            set.add(base + 100);
        }

        assertEquals(8, seen.size());
        for (int y = 0; y < 8; y++) {
            assertEquals(base + y, seen.get(y));
        }
    }

    @Test
    void clearEmptiesEverything() {
        for (long v = 0; v < 50; v++) {
            set.add(v << 42 | v);
        }

        set.clear();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
        assertFalse(set.iterator().hasNext());
    }

    @Test
    void groupShiftMustBeSane() {
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentOrderedLongSet(0));
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentOrderedLongSet(64));
    }

    /** Vanilla's eager-save scan removes through the iterator (ChunkMap.saveChunksEagerly), so the snapshot iterator removes from the live set. */
    @Test
    void iteratorRemoveDeletesFromTheLiveSet() {
        ConcurrentOrderedLongSet set = new ConcurrentOrderedLongSet(4);
        set.add(1);
        set.add(2);
        set.add(3);

        LongBidirectionalIterator iterator = set.iterator();
        assertEquals(1, iterator.nextLong());
        assertEquals(2, iterator.nextLong());
        iterator.remove();

        assertFalse(set.contains(2));
        assertEquals(2, set.size());
        assertThrows(IllegalStateException.class, iterator::remove);
    }
}
