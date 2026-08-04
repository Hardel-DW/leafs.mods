package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.longs.LongIterator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentLongSetTest {
    private final ConcurrentLongSet set = new ConcurrentLongSet();

    @Test
    void membershipOperations() {
        assertTrue(set.add(5L));
        assertFalse(set.add(5L));
        assertTrue(set.contains(5L));
        assertEquals(1, set.size());
        assertTrue(set.remove(5L));
        assertFalse(set.remove(5L));
        assertTrue(set.isEmpty());
    }

    @Test
    void iterationSeesContents() {
        set.add(1L);
        set.add(2L);
        set.add(3L);

        List<Long> values = new ArrayList<>();
        LongIterator iterator = set.iterator();
        while (iterator.hasNext()) {
            values.add(iterator.nextLong());
        }

        assertEquals(3, values.size());
        assertTrue(values.containsAll(List.of(1L, 2L, 3L)));
    }

    @Test
    void clearEmptiesTheSet() {
        set.add(1L);
        set.clear();
        assertTrue(set.isEmpty());
        assertFalse(set.iterator().hasNext());
    }
}
