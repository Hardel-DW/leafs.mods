package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import fr.hardel.leafs.chunk.ticket.TicketTimeoutIndex;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.function.BiConsumer;

/** The ticket table takes writers from any thread under one monitor; a write drains the graphs once the monitor is released. */
@Mixin(TicketStorage.class)
public abstract class TicketStorageMixin implements TicketStorageAccess {
    @Unique
    private final TicketGraphs leafs$graphs = new TicketGraphs();

    @Unique
    private TicketTimeoutIndex leafs$timeouts;

    @Override
    public TicketGraphs leafs$graphs() {
        return leafs$graphs;
    }

    @Override
    public TicketTimeoutIndex leafs$timeouts() {
        return leafs$timeouts;
    }

    @Override
    public void leafs$bindTimeouts(TicketTimeoutIndex timeouts) {
        leafs$timeouts = timeouts;
    }

    /** Vanilla's two trackers never hear a ticket again; the graphs do. */
    @WrapMethod(method = "setLoadingChunkUpdatedListener")
    private void leafs$feedTheLoadingGraph(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        original.call(leafs$graphs.loadingFeed());
    }

    @WrapMethod(method = "setSimulationChunkUpdatedListener")
    private void leafs$feedTheSimulationGraph(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        original.call(leafs$graphs.simulationFeed());
    }

    /** A stored timeout ticket enters the index with its identity; a reset re-uses the instance tracked at its first add. */
    @WrapMethod(method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean leafs$monitoredAdd(long key, Ticket ticket, Operation<Boolean> original) {
        return leafs$graphs.batch(() -> {
            synchronized (this) {
                boolean added = original.call(key, ticket);
                if (added && ticket.getType().hasTimeout()) {
                    leafs$timeouts.track(key, ticket);
                }

                return added;
            }
        });
    }

    @WrapMethod(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean leafs$monitoredRemove(long key, Ticket ticket, Operation<Boolean> original) {
        return leafs$graphs.batch(() -> {
            synchronized (this) {
                boolean removed = original.call(key, ticket);
                if (removed && ticket.getType().hasTimeout()) {
                    leafs$timeouts.untrack(key, ticket);
                }

                return removed;
            }
        });
    }

    /** The predicate decides the removal, so it is where the index learns of it. */
    @WrapMethod(method = "removeTicketIf")
    private void leafs$monitoredRemoveIf(TicketStorage.TicketPredicate predicate, Long2ObjectOpenHashMap<List<Ticket>> removedTickets, Operation<Void> original) {
        leafs$graphs.batch(() -> {
            synchronized (this) {
                original.call((TicketStorage.TicketPredicate) (ticket, chunkPos) -> {
                    boolean removed = predicate.test(ticket, chunkPos);
                    if (removed && ticket.getType().hasTimeout()) {
                        leafs$timeouts.untrack(chunkPos, ticket);
                    }

                    return removed;
                }, removedTickets);
            }
        });
    }

    @WrapMethod(method = "replaceTicketLevelOfType")
    private void leafs$monitoredReplace(int newLevel, TicketType ticketType, Operation<Void> original) {
        leafs$graphs.batch(() -> {
            synchronized (this) {
                original.call(newLevel, ticketType);
            }
        });
    }

    @WrapMethod(method = "activateAllDeactivatedTickets")
    private void leafs$monitoredActivate(Operation<Void> original) {
        leafs$graphs.batch(() -> {
            synchronized (this) {
                original.call();
            }
        });
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
}
