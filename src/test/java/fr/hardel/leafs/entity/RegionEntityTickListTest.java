package fr.hardel.leafs.entity;

import fr.hardel.leafs.region.CoordinateKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEntityTickListTest {
    private final RegionEntityTickList<String> list = new RegionEntityTickList<>();
    private final List<String> visited = new ArrayList<>();

    @Test
    void addIsVisibleToForEach() {
        list.add(1, "a", CoordinateKey.pack(0, 0));

        list.forEach(visited::add);

        assertEquals(List.of("a"), visited);
    }

    @Test
    void removeDuringIterationKeepsTheCurrentPassIntact() {
        list.add(1, "a", CoordinateKey.pack(0, 0));
        list.add(2, "b", CoordinateKey.pack(0, 0));

        list.forEach(entity -> {
            visited.add(entity);
            if (entity.equals("a")) {
                list.remove(2);
            }
        });

        assertEquals(List.of("a", "b"), visited);
        assertFalse(list.contains(2));
    }

    @Test
    void addDuringIterationWaitsForTheNextPass() {
        list.add(1, "a", CoordinateKey.pack(0, 0));

        list.forEach(entity -> {
            visited.add(entity);
            list.add(2, "b", CoordinateKey.pack(0, 0));
        });

        assertEquals(List.of("a"), visited);
        visited.clear();
        list.forEach(visited::add);
        assertEquals(List.of("a", "b"), visited);
    }

    @Test
    void queuedAddIsInvisibleUntilBeginTick() {
        list.queueAdd(1, "a", CoordinateKey.pack(0, 0));

        list.forEach(visited::add);
        assertEquals(List.of(), visited);
        assertTrue(list.contains(1));

        list.beginTick();
        list.forEach(visited::add);
        assertEquals(List.of("a"), visited);
    }

    @Test
    void splitRebucketsBySectionAndDropsOrphans() {
        RegionEntityTickList<String> child = new RegionEntityTickList<>();
        list.add(1, "kept", CoordinateKey.pack(0, 0));
        list.add(2, "dropped", CoordinateKey.pack(3, 0));
        list.queueAdd(3, "pending", CoordinateKey.pack(1, 1));

        list.splitInto(1, section -> section == CoordinateKey.pack(0, 0) ? child : null);

        assertEquals(0, list.size());
        assertTrue(child.contains(1));
        assertFalse(child.contains(2));
        assertTrue(child.contains(3));
        child.forEach(visited::add);
        assertEquals(List.of("kept"), visited);
    }

    @Test
    void mergeMovesEverythingIncludingPending() {
        RegionEntityTickList<String> target = new RegionEntityTickList<>();
        list.add(1, "a", CoordinateKey.pack(0, 0));
        list.queueAdd(2, "b", CoordinateKey.pack(1, 0));

        list.mergeInto(target);

        assertEquals(0, list.size());
        assertTrue(target.contains(1));
        assertTrue(target.contains(2));
        target.beginTick();
        target.forEach(visited::add);
        assertEquals(List.of("a", "b"), visited);
    }
}
