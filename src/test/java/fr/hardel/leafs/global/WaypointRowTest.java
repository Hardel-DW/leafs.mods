package fr.hardel.leafs.global;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;
import net.minecraft.world.waypoints.WaypointTransmitter.Connection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointRowTest {

    /** A transmitter that only serves as a cell key. */
    private static final class Beacon implements WaypointTransmitter {
        @Override
        public boolean isTransmittingWaypoint() {
            return true;
        }

        @Override
        public Optional<Connection> makeWaypointConnectionWith(ServerPlayer player) {
            return Optional.empty();
        }

        @Override
        public Waypoint.Icon waypointIcon() {
            return null;
        }
    }

    /** Records its life in the journal; {@code broken} decides what the next update does with it. */
    private static final class Link implements Connection {
        private final List<String> journal;
        private final String name;
        boolean broken;

        Link(List<String> journal, String name) {
            this.journal = journal;
            this.name = name;
        }

        @Override
        public void connect() {
            journal.add(name + " connect");
        }

        @Override
        public void disconnect() {
            journal.add(name + " disconnect");
        }

        @Override
        public void update() {
            journal.add(name + " update");
        }

        @Override
        public boolean isBroken() {
            return broken;
        }
    }

    @Test
    void aMadeConnectionTakesTheCellAndNoneDropsThePreviousOne() {
        List<String> journal = new ArrayList<>();
        WaypointRow row = new WaypointRow();
        Beacon beacon = new Beacon();
        Link first = new Link(journal, "first");

        row.connect(beacon, () -> Optional.of(first));
        assertTrue(row.has(beacon));
        row.connect(beacon, Optional::empty);
        assertFalse(row.has(beacon));
        assertEquals(List.of("first connect", "first disconnect"), journal);
    }

    @Test
    void anUpdateKeepsALiveConnectionAndRemakesOrDropsABrokenOne() {
        List<String> journal = new ArrayList<>();
        WaypointRow row = new WaypointRow();
        Beacon beacon = new Beacon();
        Link first = new Link(journal, "first");
        Link second = new Link(journal, "second");
        row.connect(beacon, () -> Optional.of(first));

        row.update(beacon, () -> Optional.of(second));
        first.broken = true;
        row.update(beacon, () -> Optional.of(second));
        second.broken = true;
        row.update(beacon, Optional::empty);

        assertFalse(row.has(beacon));
        assertEquals(List.of("first connect", "first update", "second connect", "second disconnect"), journal);
    }

    @Test
    void anUpdateOfAnAbsentCellMakesNothing() {
        List<String> journal = new ArrayList<>();
        WaypointRow row = new WaypointRow();
        Beacon beacon = new Beacon();

        row.update(beacon, () -> Optional.of(new Link(journal, "ghost")));

        assertFalse(row.has(beacon));
        assertTrue(journal.isEmpty());
    }

    @Test
    void disconnectAllEmptiesTheRow() {
        List<String> journal = new ArrayList<>();
        WaypointRow row = new WaypointRow();
        Beacon near = new Beacon();
        Beacon far = new Beacon();
        row.connect(near, () -> Optional.of(new Link(journal, "near")));
        row.connect(far, () -> Optional.of(new Link(journal, "far")));

        row.disconnectAll();

        assertFalse(row.has(near));
        assertFalse(row.has(far));
        assertEquals(2, journal.stream().filter(line -> line.endsWith("disconnect")).count());
    }
}
