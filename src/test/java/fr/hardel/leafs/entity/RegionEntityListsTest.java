package fr.hardel.leafs.entity;

import fr.hardel.leafs.region.CoordinateKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEntityListsTest {
    private final RegionEntityTickList<String> west = new RegionEntityTickList<>();
    private final RegionEntityTickList<String> east = new RegionEntityTickList<>();
    private final RegionEntityLists<String> lists = new RegionEntityLists<>(chunkKey -> CoordinateKey.x(chunkKey) < 0 ? west : east);
    private final List<String> visited = new ArrayList<>();

    @Test
    void addedRoutesToTheOwningList() {
        lists.entityAdded(1, "a", CoordinateKey.pack(-5, 0));

        assertTrue(west.contains(1));
        assertFalse(east.contains(1));
    }

    @Test
    void crossRegionMoveBuffersAtTheTarget() {
        lists.entityAdded(1, "a", CoordinateKey.pack(-5, 0));

        lists.entityMoved(1, "a", CoordinateKey.pack(-5, 0), CoordinateKey.pack(5, 0));

        assertFalse(west.contains(1));
        assertTrue(east.contains(1));
        east.forEach(visited::add);
        assertEquals(List.of(), visited);
        east.beginTick();
        east.forEach(visited::add);
        assertEquals(List.of("a"), visited);
    }

    @Test
    void sameRegionMoveStaysImmediate() {
        lists.entityAdded(1, "a", CoordinateKey.pack(2, 0));

        lists.entityMoved(1, "a", CoordinateKey.pack(2, 0), CoordinateKey.pack(3, 0));

        east.forEach(visited::add);
        assertEquals(List.of("a"), visited);
    }

    @Test
    void removedLeavesTheOwningList() {
        lists.entityAdded(1, "a", CoordinateKey.pack(5, 0));

        lists.entityRemoved(1, CoordinateKey.pack(5, 0));

        assertFalse(east.contains(1));
    }
}
