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
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/**
 * Chantier propagateur, S1: the ticket table takes writers from any thread under one monitor, and the
 * tracker listeners route themselves, inline on the level-serial side, queued to it from anywhere
 * else, because the propagation graph they feed stays single-threaded until S4.
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

    /** The loading listener also feeds the Leafs propagator, and skips vanilla's graph when it drives (S4). */
    @WrapMethod(method = "setLoadingChunkUpdatedListener")
    private void leafs$routeLoadingListener(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        TicketStorage.ChunkUpdated routed = leafs$routed(listener);
        TicketStorage.ChunkUpdated shim = (key, level, onlyDecreased) -> {
            ServerLevel owner = leafs$level;
            LevelTicketPropagator propagator = owner == null ? null : ((PropagatorAccess) owner.getChunkSource().chunkMap.getDistanceManager()).leafs$propagator();
            if (propagator != null) {
                propagator.feed(key);
                if (!propagator.shadow()) {
                    return;
                }
            }

            routed.update(key, level, onlyDecreased);
        };
        original.call(shim);
    }

    @WrapMethod(method = "setSimulationChunkUpdatedListener")
    private void leafs$routeSimulationListener(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        original.call(leafs$routed(listener));
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
