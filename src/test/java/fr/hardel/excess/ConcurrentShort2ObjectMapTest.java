package fr.hardel.excess;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentShort2ObjectMapTest {
    private final ConcurrentShort2ObjectMap<String> map = new ConcurrentShort2ObjectMap<>();

    @Test
    void pointOperationsAndDefaultReturnValue() {
        assertNull(map.get((short) 1));
        assertNull(map.put((short) 1, "a"));
        assertEquals("a", map.get((short) 1));
        assertEquals("a", map.put((short) 1, "b"));
        assertTrue(map.containsKey((short) 1));
        assertTrue(map.containsValue("b"));
        assertEquals(1, map.size());
        assertEquals("b", map.remove((short) 1));
        assertNull(map.remove((short) 1));
        assertFalse(map.containsKey((short) 1));
        assertTrue(map.isEmpty());
    }

    /** Vanilla's refresh copies the section's records into an open hash map through the entry set. */
    @Test
    void aFastutilCopyReadsTheEntrySet() {
        map.put((short) 3, "c");
        map.put((short) 4, "d");

        Short2ObjectOpenHashMap<String> copy = new Short2ObjectOpenHashMap<>(map);

        assertEquals(2, copy.size());
        assertEquals("c", copy.get((short) 3));
        assertEquals("d", copy.get((short) 4));
    }

    @Test
    void iterationSurvivesConcurrentMutation() {
        for (short i = 0; i < 100; i++) {
            map.put(i, "v" + i);
        }

        int seen = 0;
        List<String> values = new ArrayList<>();
        for (String value : map.values()) {
            assertTrue(value.startsWith("v") || value.startsWith("x"), "value outside the known universe: " + value);
            map.remove((short) (90 - seen));
            map.put((short) (200 + seen), "x" + seen);
            values.add(value);
            seen++;
        }

        assertTrue(seen > 0);
    }

    /** A fixed-size stream over a growing map throws once it sees more than it was told. */
    @Test
    void aStreamOverTheValuesSurvivesGrowth() {
        map.put((short) 1, "a");
        map.put((short) 2, "b");

        List<String> seen = map.values().stream().peek(_ -> map.put((short) (map.size() + 10), "z")).toList();

        assertTrue(seen.size() >= 2);
    }
}
