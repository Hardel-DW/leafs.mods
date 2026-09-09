package fr.hardel.leafs.global;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;
import net.minecraft.world.waypoints.WaypointTransmitter.Connection;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Vanilla's waypoint manager without its lock: a row per receiver, written cell by cell from every region. With the locator bar off, nothing is walked. */
public final class ConcurrentWaypointManager extends ServerWaypointManager {
    private final ServerLevel level;
    private final Set<WaypointTransmitter> waypoints = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ServerPlayer, WaypointRow> rows = new ConcurrentHashMap<>();

    public ConcurrentWaypointManager(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void trackWaypoint(@NonNull WaypointTransmitter waypoint) {
        waypoints.add(waypoint);
        if (!enabled()) {
            return;
        }

        rows.forEach((player, row) -> row.connect(waypoint, maker(player, waypoint)));
    }

    /** The transmitter's side of a move: every receiver's cell for it. */
    @Override
    public void updateWaypoint(@NonNull WaypointTransmitter waypoint) {
        if (!enabled() || !waypoints.contains(waypoint)) {
            return;
        }

        rows.forEach((player, row) -> refresh(row, player, waypoint));
    }

    @Override
    public void untrackWaypoint(@NonNull WaypointTransmitter waypoint) {
        waypoints.remove(waypoint);
        for (WaypointRow row : rows.values()) {
            row.disconnect(waypoint);
        }
    }

    @Override
    public void addPlayer(@NonNull ServerPlayer player) {
        WaypointRow row = rows.computeIfAbsent(player, _ -> new WaypointRow());
        if (enabled()) {
            for (WaypointTransmitter waypoint : waypoints) {
                row.connect(waypoint, maker(player, waypoint));
            }
        }

        if (player.isTransmittingWaypoint()) {
            trackWaypoint(player);
        }
    }

    /** The receiver's side of a move: its whole row. */
    @Override
    public void updatePlayer(@NonNull ServerPlayer player) {
        WaypointRow row = rows.get(player);
        if (row == null || !enabled()) {
            return;
        }

        for (WaypointTransmitter waypoint : waypoints) {
            refresh(row, player, waypoint);
        }
    }

    @Override
    public void removePlayer(@NonNull ServerPlayer player) {
        WaypointRow row = rows.remove(player);
        if (row != null) {
            row.disconnectAll();
        }

        untrackWaypoint(player);
    }

    @Override
    public void breakAllConnections() {
        for (WaypointRow row : rows.values()) {
            row.disconnectAll();
        }
    }

    @Override
    public void remakeConnections(@NonNull WaypointTransmitter waypoint) {
        if (!enabled()) {
            return;
        }

        rows.forEach((player, row) -> row.connect(waypoint, maker(player, waypoint)));
    }

    @Override
    public @NonNull Set<WaypointTransmitter> transmitters() {
        return waypoints;
    }

    private void refresh(WaypointRow row, ServerPlayer player, WaypointTransmitter waypoint) {
        if (row.has(waypoint)) {
            row.update(waypoint, maker(player, waypoint));
        } else {
            row.connect(waypoint, maker(player, waypoint));
        }
    }

    /** Asked inside the cell step, so a transmitter untracked meanwhile makes no connection; a receiver never connects to itself, as in vanilla. */
    private Supplier<Optional<Connection>> maker(ServerPlayer player, WaypointTransmitter waypoint) {
        return () -> player != waypoint && waypoints.contains(waypoint) ? waypoint.makeWaypointConnectionWith(player) : Optional.empty();
    }

    private boolean enabled() {
        return level.getGameRules().get(GameRules.LOCATOR_BAR);
    }
}
