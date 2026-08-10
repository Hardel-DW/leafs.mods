package fr.hardel.leafs.entity;

import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.world.WorldTickContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEntityTickListTest {
    private final RegionEntityData westHome = new RegionEntityData();
    private final RegionEntityData eastHome = new RegionEntityData();
    private final RegionEntityTickList<String> west = new RegionEntityTickList<>(westHome);
    private final RegionEntityTickList<String> east = new RegionEntityTickList<>(eastHome);
    private final List<String> visited = new ArrayList<>();

    @AfterEach
    void leaveContext() {
        WorldTickContext.exit();
    }

    private void own(RegionEntityData home) {
        WorldTickContext.exit();
        WorldTickContext.enter(new Object(), null, home);
    }

    @Test
    void ownerMutationsAreImmediate() {
        own(westHome);
        west.add(1, "a", CoordinateKey.pack(-5, 0));

        assertTrue(west.contains(1));
        west.forEach(visited::add);
        assertEquals(List.of("a"), visited);

        west.remove(1);
        assertFalse(west.contains(1));
    }

    @Test
    void queuedArrivalBuffersToTheNextPass() {
        own(eastHome);
        east.queueAdd(1, "a", CoordinateKey.pack(5, 0));

        assertTrue(east.contains(1));
        east.forEach(visited::add);
        assertEquals(List.of(), visited);

        east.beginTick();
        east.forEach(visited::add);
        assertEquals(List.of("a"), visited);
    }

    @Test
    void foreignMutationWaitsForTheOwnerTick() {
        west.add(1, "a", CoordinateKey.pack(-5, 0));

        assertTrue(west.contains(1));
        west.forEach(visited::add);
        assertEquals(List.of(), visited);

        own(westHome);
        west.beginTick();
        west.forEach(visited::add);
        assertEquals(List.of("a"), visited);
    }

    @Test
    void foreignRemoveOverridesAQueuedAdd() {
        west.queueAdd(1, "a", CoordinateKey.pack(-5, 0));
        west.remove(1);

        assertFalse(west.contains(1));

        own(westHome);
        west.beginTick();
        west.forEach(visited::add);
        assertEquals(List.of(), visited);
    }

    @Test
    void foreignRemoveReachesAnActiveEntity() {
        own(westHome);
        west.add(1, "a", CoordinateKey.pack(-5, 0));

        own(eastHome);
        west.remove(1);
        assertFalse(west.contains(1));

        own(westHome);
        west.beginTick();
        west.forEach(visited::add);
        assertEquals(List.of(), visited);
    }

    @Test
    void mergeFoldsThePendingMailboxFirst() {
        west.queueAdd(1, "a", CoordinateKey.pack(-5, 0));

        west.mergeInto(east);

        assertFalse(west.contains(1));
        assertTrue(east.contains(1));
        own(eastHome);
        east.forEach(visited::add);
        assertEquals(List.of("a"), visited);
    }

    @Test
    void foreignMoveRebucketsOnSplit() {
        own(westHome);
        west.add(1, "a", CoordinateKey.pack(-5, 0));

        own(eastHome);
        west.move(1, CoordinateKey.pack(-40, 0));

        own(westHome);
        west.beginTick();
        RegionEntityData farHome = new RegionEntityData();
        RegionEntityTickList<String> far = new RegionEntityTickList<>(farHome);
        west.splitInto(4, section -> CoordinateKey.x(section) <= -3 ? far : west);

        assertTrue(far.contains(1));
        assertFalse(west.contains(1));
    }
}
