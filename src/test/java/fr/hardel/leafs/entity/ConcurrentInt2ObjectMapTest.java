package fr.hardel.leafs.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentInt2ObjectMapTest {
    private final ConcurrentInt2ObjectMap<String> map = new ConcurrentInt2ObjectMap<>();

    @Test
    void pointOperationsAndDefaultReturnValue() {
        assertNull(map.get(1));
        assertNull(map.put(1, "a"));
        assertEquals("a", map.get(1));
        assertEquals("a", map.put(1, "b"));
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue("b"));
        assertEquals(1, map.size());
        assertEquals("b", map.remove(1));
        assertNull(map.remove(1));
        assertFalse(map.containsKey(1));
        assertTrue(map.isEmpty());
    }

    @Test
    void valuesViewIteratesEverythingWithoutOrderGuarantee() {
        for (int i = 0; i < 50; i++) {
            map.put(i, "v" + i);
        }

        List<String> values = new ArrayList<>();
        map.values().iterator().forEachRemaining(values::add);
        assertEquals(50, values.size());
        for (int i = 0; i < 50; i++) {
            assertTrue(values.contains("v" + i));
        }
    }

    @Test
    void entrySetReflectsTheMap() {
        map.put(3, "c");

        assertEquals(1, map.int2ObjectEntrySet().size());
        map.int2ObjectEntrySet().forEach(entry -> {
            assertEquals(3, entry.getIntKey());
            assertEquals("c", entry.getValue());
        });
    }

    @Test
    void iterationSurvivesConcurrentMutation() {
        for (int i = 0; i < 100; i++) {
            map.put(i, "v" + i);
        }

        int seen = 0;
        for (String value : map.values()) {
            assertTrue(value.startsWith("v") || value.startsWith("x"), "value outside the known universe: " + value);
            map.remove(90 - seen);
            map.put(200 + seen, "x" + seen);
            seen++;
        }

        assertTrue(seen > 0);
    }

    @Test
    void clearEmptiesTheMap() {
        map.put(1, "a");
        map.put(2, "b");

        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.int2ObjectEntrySet().size());
    }
}
