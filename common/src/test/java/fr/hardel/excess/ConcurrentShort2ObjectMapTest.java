package fr.hardel.excess;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

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

    @Test
    void aFastutilCopyReadsTheEntrySet() {
        map.put((short) 3, "c");
        map.put((short) 4, "d");

        Short2ObjectOpenHashMap<String> copy = new Short2ObjectOpenHashMap<>(map);

        assertEquals(2, copy.size());
        assertEquals("c", copy.get((short) 3));
        assertEquals("d", copy.get((short) 4));
    }
}
