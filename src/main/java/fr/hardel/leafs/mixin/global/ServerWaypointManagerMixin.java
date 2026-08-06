package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

/** Serializes waypoint table mutations from region threads and the global phase. {@code transmitters()} returns a snapshot. */
@Mixin(ServerWaypointManager.class)
public abstract class ServerWaypointManagerMixin {

    @WrapMethod(method = "trackWaypoint")
    private void leafs$lockedTrackWaypoint(WaypointTransmitter waypoint, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(waypoint));
    }

    @WrapMethod(method = "updateWaypoint")
    private void leafs$lockedUpdateWaypoint(WaypointTransmitter waypoint, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(waypoint));
    }

    @WrapMethod(method = "untrackWaypoint")
    private void leafs$lockedUntrackWaypoint(WaypointTransmitter waypoint, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(waypoint));
    }

    @WrapMethod(method = "addPlayer")
    private void leafs$lockedAddPlayer(ServerPlayer player, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(player));
    }

    @WrapMethod(method = "updatePlayer")
    private void leafs$lockedUpdatePlayer(ServerPlayer player, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(player));
    }

    @WrapMethod(method = "removePlayer")
    private void leafs$lockedRemovePlayer(ServerPlayer player, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(player));
    }

    @WrapMethod(method = "breakAllConnections")
    private void leafs$lockedBreakAllConnections(Operation<Void> original) {
        SharedStateMonitor.run(this, original::call);
    }

    @WrapMethod(method = "remakeConnections")
    private void leafs$lockedRemakeConnections(WaypointTransmitter waypoint, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(waypoint));
    }

    @WrapMethod(method = "transmitters")
    private Set<WaypointTransmitter> leafs$lockedTransmitters(Operation<Set<WaypointTransmitter>> original) {
        return SharedStateMonitor.call(this, () -> Set.copyOf(original.call()));
    }
}
