package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Chantier propagateur, S1: the ticket table takes writers from any thread under one monitor, and the
 * tracker listeners route themselves, inline on the level-serial side, queued to it from anywhere
 * else, because the propagation graph they feed stays single-threaded until S4. The serial side reads
 * the table bare, so every read entry point takes the same monitor against off-level writers.
 */
@Mixin(TicketStorage.class)
public abstract class TicketStorageMixin implements TicketStorageAccess {

    @Unique
    private volatile ServerLevel leafs$level;

    @Override
    public void leafs$bindLevel(ServerLevel level) {
        this.leafs$level = level;
    }

    @WrapMethod(method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean leafs$monitoredAdd(long key, Ticket ticket, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(key, ticket);
        }
    }

    @WrapMethod(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean leafs$monitoredRemove(long key, Ticket ticket, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(key, ticket);
        }
    }

    @WrapMethod(method = "removeTicketIf")
    private void leafs$monitoredRemoveIf(TicketStorage.TicketPredicate predicate, Long2ObjectOpenHashMap<List<Ticket>> removedTickets, Operation<Void> original) {
        synchronized (this) {
            original.call(predicate, removedTickets);
        }
    }

    @WrapMethod(method = "replaceTicketLevelOfType")
    private void leafs$monitoredReplace(int newLevel, TicketType ticketType, Operation<Void> original) {
        synchronized (this) {
            original.call(newLevel, ticketType);
        }
    }

    @WrapMethod(method = "activateAllDeactivatedTickets")
    private void leafs$monitoredActivate(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }

    @WrapMethod(method = "getTicketLevelAt(JZ)I")
    private int leafs$monitoredLevelRead(long key, boolean simulation, Operation<Integer> original) {
        synchronized (this) {
            return original.call(key, simulation);
        }
    }

    /** The copy is what makes the read safe: vanilla iterates the returned list outside any monitor. */
    @WrapMethod(method = "getTickets")
    private List<Ticket> leafs$monitoredTicketsRead(long key, Operation<List<Ticket>> original) {
        synchronized (this) {
            return List.copyOf(original.call(key));
        }
    }

    @WrapMethod(method = "forEachTicket(Ljava/util/function/BiConsumer;)V")
    private void leafs$monitoredIteration(BiConsumer<ChunkPos, Ticket> output, Operation<Void> original) {
        synchronized (this) {
            original.call(output);
        }
    }

    @WrapMethod(method = "shouldKeepDimensionActive")
    private boolean leafs$monitoredActivityRead(Operation<Boolean> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "hasTickets")
    private boolean leafs$monitoredEmptinessRead(Operation<Boolean> original) {
        synchronized (this) {
            return original.call();
        }
    }

    /**
     * Driving (S4): the propagator is fed inline under the monitor and vanilla's graph is skipped.
     * Shadow (S5): the feed rides the routed listener task, so both propagators see the exact same
     * update order on the serial thread and the comparison is never ahead of vanilla.
     */
    @WrapMethod(method = "setLoadingChunkUpdatedListener")
    private void leafs$routeLoadingListener(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        TicketStorage.ChunkUpdated shadowPair = leafs$routed((key, level, onlyDecreased) -> {
            LevelTicketPropagator propagator = leafs$propagator();
            if (propagator != null) {
                propagator.feed(key, level);
            }

            listener.update(key, level, onlyDecreased);
        });
        TicketStorage.ChunkUpdated shim = (key, level, onlyDecreased) -> {
            LevelTicketPropagator propagator = leafs$propagator();
            if (propagator != null && !propagator.shadow()) {
                propagator.feed(key, level);
                return;
            }

            shadowPair.update(key, level, onlyDecreased);
        };
        original.call(shim);
    }

    @WrapMethod(method = "setSimulationChunkUpdatedListener")
    private void leafs$routeSimulationListener(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        original.call(leafs$routed(listener));
    }

    @Unique
    private LevelTicketPropagator leafs$propagator() {
        ServerLevel owner = leafs$level;
        return owner == null ? null : ((PropagatorAccess) owner.getChunkSource().chunkMap.getDistanceManager()).leafs$propagator();
    }

    @Unique
    private TicketStorage.ChunkUpdated leafs$routed(TicketStorage.ChunkUpdated listener) {
        if (listener == null) {
            return null;
        }

        return (key, level, onlyDecreased) -> {
            ServerLevel owner = leafs$level;
            if (owner == null || owner.getServer().isSameThread()) {
                listener.update(key, level, onlyDecreased);
                return;
            }

            ((LeafsServerAccess) owner.getServer()).leafs$ticking().submitToLevel(owner, () -> listener.update(key, level, onlyDecreased));
        };
    }
}
