package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/** Server-side scoreboard lock: dirty flag, tracked objectives, packet builds and save serialized with the base half. */
@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardMixin {

    @WrapMethod(method = "load")
    private void leafs$lockedLoad(ScoreboardSaveData.Packed data, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(data));
    }

    @WrapMethod(method = "onScoreChanged")
    private void leafs$lockedOnScoreChanged(ScoreHolder holder, Objective objective, Score score, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(holder, objective, score));
    }

    @WrapMethod(method = "onScoreLockChanged")
    private void leafs$lockedOnScoreLockChanged(ScoreHolder holder, Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(holder, objective));
    }

    @WrapMethod(method = "onPlayerRemoved")
    private void leafs$lockedOnPlayerRemoved(ScoreHolder holder, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(holder));
    }

    @WrapMethod(method = "onPlayerScoreRemoved")
    private void leafs$lockedOnPlayerScoreRemoved(ScoreHolder holder, Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(holder, objective));
    }

    @WrapMethod(method = "setDisplayObjective")
    private void leafs$lockedSetDisplayObjective(DisplaySlot slot, Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(slot, objective));
    }

    @WrapMethod(method = "addPlayerToTeam")
    private boolean leafs$lockedAddPlayerToTeam(String player, PlayerTeam team, Operation<Boolean> original) {
        return SharedStateMonitor.call(this, () -> original.call(player, team));
    }

    @WrapMethod(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V")
    private void leafs$lockedRemovePlayerFromTeam(String player, PlayerTeam team, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(player, team));
    }

    @WrapMethod(method = "onObjectiveAdded")
    private void leafs$lockedOnObjectiveAdded(Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(objective));
    }

    @WrapMethod(method = "onObjectiveChanged")
    private void leafs$lockedOnObjectiveChanged(Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(objective));
    }

    @WrapMethod(method = "onObjectiveRemoved")
    private void leafs$lockedOnObjectiveRemoved(Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(objective));
    }

    @WrapMethod(method = "onTeamAdded")
    private void leafs$lockedOnTeamAdded(PlayerTeam team, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(team));
    }

    @WrapMethod(method = "onTeamChanged")
    private void leafs$lockedOnTeamChanged(PlayerTeam team, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(team));
    }

    @WrapMethod(method = "onTeamRemoved")
    private void leafs$lockedOnTeamRemoved(PlayerTeam team, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(team));
    }

    @WrapMethod(method = "storeToSaveDataIfDirty")
    private void leafs$lockedStoreToSaveData(ScoreboardSaveData saveData, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(saveData));
    }

    @WrapMethod(method = "getStartTrackingPackets")
    private List<Packet<?>> leafs$lockedGetStartTrackingPackets(Objective objective, Operation<List<Packet<?>>> original) {
        return SharedStateMonitor.call(this, () -> original.call(objective));
    }

    @WrapMethod(method = "getStopTrackingPackets")
    private List<Packet<?>> leafs$lockedGetStopTrackingPackets(Objective objective, Operation<List<Packet<?>>> original) {
        return SharedStateMonitor.call(this, () -> original.call(objective));
    }

    @WrapMethod(method = "startTrackingObjective")
    private void leafs$lockedStartTrackingObjective(Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(objective));
    }

    @WrapMethod(method = "stopTrackingObjective")
    private void leafs$lockedStopTrackingObjective(Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(objective));
    }
}
