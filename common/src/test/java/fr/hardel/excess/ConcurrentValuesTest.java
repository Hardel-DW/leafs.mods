package fr.hardel.excess;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentValuesTest {
    private final ConcurrentHashMap<Integer, String> map = new ConcurrentHashMap<>();
    private final ConcurrentValues<String> values = new ConcurrentValues<>(map);

    @Test
    void iterationSurvivesConcurrentMutation() {
        for (int i = 0; i < 100; i++) {
            map.put(i, "v%d".formatted(i));
        }

        int seen = 0;
        for (String value : values) {
            assertTrue(value.startsWith("v") || value.startsWith("x"), "value outside the known universe: %s".formatted(value));
            map.remove(90 - seen);
            map.put(200 + seen, "x%d".formatted(seen));
            seen++;
        }

        assertTrue(seen > 0);
    }

    @Test
    void aStreamOverTheValuesSurvivesGrowth() {
        map.put(1, "a");
        map.put(2, "b");

        List<String> seen = values.stream().peek(_ -> map.put(map.size() + 10, "z")).toList();

        assertTrue(seen.size() >= 2);
    }
}
