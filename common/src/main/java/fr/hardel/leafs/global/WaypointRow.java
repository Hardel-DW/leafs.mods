package fr.hardel.leafs.global;

import net.minecraft.world.waypoints.WaypointTransmitter;
import net.minecraft.world.waypoints.WaypointTransmitter.Connection;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class WaypointRow {
    private final ConcurrentHashMap<WaypointTransmitter, Connection> cells = new ConcurrentHashMap<>();

    public boolean has(WaypointTransmitter waypoint) {
        return cells.containsKey(waypoint);
    }

    public void connect(WaypointTransmitter waypoint, Supplier<Optional<Connection>> maker) {
        cells.compute(waypoint, (_, current) -> {
            Optional<Connection> made = maker.get();
            if (made.isPresent()) {
                made.get().connect();
                return made.get();
            }

            if (current != null) {
                current.disconnect();
            }

            return null;
        });
    }

    public void update(WaypointTransmitter waypoint, Supplier<Optional<Connection>> maker) {
        cells.computeIfPresent(waypoint, (_, current) -> {
            if (!current.isBroken()) {
                current.update();
                return current;
            }

            Optional<Connection> made = maker.get();
            if (made.isPresent()) {
                made.get().connect();
                return made.get();
            }

            current.disconnect();
            return null;
        });
    }

    public void disconnect(WaypointTransmitter waypoint) {
        cells.computeIfPresent(waypoint, (_, current) -> {
            current.disconnect();
            return null;
        });
    }

    public void disconnectAll() {
        for (WaypointTransmitter waypoint : cells.keySet()) {
            disconnect(waypoint);
        }
    }
}
