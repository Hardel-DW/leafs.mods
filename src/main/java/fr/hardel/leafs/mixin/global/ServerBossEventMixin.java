package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;
import java.util.List;

/** Watched by players of every region, written by commands on the global thread and by disconnects on region threads. */
@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin {

    @WrapMethod(method = "addPlayer")
    private void leafs$lockedAddPlayer(ServerPlayer player, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(player));
    }

    @WrapMethod(method = "removePlayer")
    private void leafs$lockedRemovePlayer(ServerPlayer player, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(player));
    }

    @WrapMethod(method = "removeAllPlayers")
    private void leafs$lockedRemoveAllPlayers(Operation<Void> original) {
        SharedStateMonitor.run(this, original::call);
    }

    @WrapMethod(method = "setVisible")
    private void leafs$lockedSetVisible(boolean visible, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(visible));
    }

    @WrapMethod(method = "getPlayers")
    private Collection<ServerPlayer> leafs$lockedGetPlayers(Operation<Collection<ServerPlayer>> original) {
        return SharedStateMonitor.call(this, () -> List.copyOf(original.call()));
    }
}
