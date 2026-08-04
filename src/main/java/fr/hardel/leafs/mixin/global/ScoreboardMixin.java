package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * The #23 lock, base half: structural mutators and iterating readers serialize on the scoreboard
 * instance (kills and stats reach it from region workers). Per-key getters stay lock-free: fastutil
 * open-hash reads on a stale array terminate, so mutual exclusion of writers is the safety line.
 * {@code ScoreAccess} value writes stay outside the lock (one owning region per holder); their
 * dirty/broadcast tail re-enters it through {@code onScoreChanged}.
 */
@Mixin(Scoreboard.class)
public abstract class ScoreboardMixin {

    @WrapMethod(method = "addObjective")
    private Objective leafs$lockedAddObjective(String name, ObjectiveCriteria criteria, Component displayName, ObjectiveCriteria.RenderType renderType, boolean displayAutoUpdate, NumberFormat numberFormat, Operation<Objective> original) {
        return SharedStateMonitor.call(this, () -> original.call(name, criteria, displayName, renderType, displayAutoUpdate, numberFormat));
    }

    @WrapMethod(method = "forAllObjectives")
    private void leafs$lockedForAllObjectives(ObjectiveCriteria criteria, ScoreHolder holder, Consumer<ScoreAccess> action, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(criteria, holder, action));
    }

    @WrapMethod(method = "getOrCreatePlayerScore(Lnet/minecraft/world/scores/ScoreHolder;Lnet/minecraft/world/scores/Objective;Z)Lnet/minecraft/world/scores/ScoreAccess;")
    private ScoreAccess leafs$lockedGetOrCreatePlayerScore(ScoreHolder holder, Objective objective, boolean forceWritable, Operation<ScoreAccess> original) {
        return SharedStateMonitor.call(this, () -> original.call(holder, objective, forceWritable));
    }

    @WrapMethod(method = "getPlayerScoreInfo")
    private ReadOnlyScoreInfo leafs$lockedGetPlayerScoreInfo(ScoreHolder holder, Objective objective, Operation<ReadOnlyScoreInfo> original) {
        return SharedStateMonitor.call(this, () -> original.call(holder, objective));
    }

    @WrapMethod(method = "listPlayerScores(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;")
    private Collection<PlayerScoreEntry> leafs$lockedListObjectiveScores(Objective objective, Operation<Collection<PlayerScoreEntry>> original) {
        return SharedStateMonitor.call(this, () -> original.call(objective));
    }

    @WrapMethod(method = "listPlayerScores(Lnet/minecraft/world/scores/ScoreHolder;)Lit/unimi/dsi/fastutil/objects/Object2IntMap;")
    private Object2IntMap<Objective> leafs$lockedListHolderScores(ScoreHolder holder, Operation<Object2IntMap<Objective>> original) {
        return SharedStateMonitor.call(this, () -> original.call(holder));
    }

    @WrapMethod(method = "resetAllPlayerScores")
    private void leafs$lockedResetAllPlayerScores(ScoreHolder holder, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(holder));
    }

    @WrapMethod(method = "resetSinglePlayerScore")
    private void leafs$lockedResetSinglePlayerScore(ScoreHolder holder, Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(holder, objective));
    }

    @WrapMethod(method = "removeObjective")
    private void leafs$lockedRemoveObjective(Objective objective, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(objective));
    }

    @WrapMethod(method = "addPlayerTeam")
    private PlayerTeam leafs$lockedAddPlayerTeam(String name, Operation<PlayerTeam> original) {
        return SharedStateMonitor.call(this, () -> original.call(name));
    }

    @WrapMethod(method = "removePlayerTeam")
    private void leafs$lockedRemovePlayerTeam(PlayerTeam team, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(team));
    }

    @WrapMethod(method = "removePlayerFromTeam(Ljava/lang/String;)Z")
    private boolean leafs$lockedRemovePlayerFromAnyTeam(String player, Operation<Boolean> original) {
        return SharedStateMonitor.call(this, () -> original.call(player));
    }

    @WrapMethod(method = "entityRemoved")
    private void leafs$lockedEntityRemoved(Entity entity, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(entity));
    }
}
