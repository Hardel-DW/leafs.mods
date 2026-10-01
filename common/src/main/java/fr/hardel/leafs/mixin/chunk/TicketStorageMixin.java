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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.BiConsumer;

@Mixin(TicketStorage.class)
public abstract class TicketStorageMixin implements TicketStorageAccess {
    @Unique
    private TicketGraphs leafs$graphs;

    @Unique
    private TicketTimeoutIndex leafs$timeouts;

    @Unique
    private volatile int leafs$keepingActive;

    @Override
    public void leafs$bind(TicketGraphs graphs, TicketTimeoutIndex timeouts) {
        leafs$graphs = graphs;
        leafs$timeouts = timeouts;
    }

    @WrapMethod(method = "setLoadingChunkUpdatedListener")
    private void leafs$feedTheLoadingGraph(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        original.call((TicketStorage.ChunkUpdated) (key, level, _) -> leafs$graphs.loading().setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level));
    }

    @WrapMethod(method = "setSimulationChunkUpdatedListener")
    private void leafs$feedTheSimulationGraph(TicketStorage.ChunkUpdated listener, Operation<Void> original) {
        original.call((TicketStorage.ChunkUpdated) (key, level, _) -> leafs$graphs.simulation().setSource(ChunkPos.getX(key), ChunkPos.getZ(key), level));
    }

    @WrapMethod(method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean leafs$monitoredAdd(long key, Ticket ticket, Operation<Boolean> original) {
        synchronized (this) {
            boolean added = original.call(key, ticket);
            if (added) {
                leafs$entered(key, ticket);
            }

            return added;
        }
    }

    @WrapMethod(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean leafs$monitoredRemove(long key, Ticket ticket, Operation<Boolean> original) {
        synchronized (this) {
            boolean removed = original.call(key, ticket);
            if (removed) {
                leafs$left(key, ticket);
            }

            return removed;
        }
    }

    @WrapMethod(method = "removeTicketIf")
    private void leafs$monitoredRemoveIf(TicketStorage.TicketPredicate predicate, Long2ObjectOpenHashMap<List<Ticket>> removedTickets, Operation<Void> original) {
        synchronized (this) {
            original.call((TicketStorage.TicketPredicate) (ticket, chunkPos) -> {
                boolean removed = predicate.test(ticket, chunkPos);
                if (removed) {
                    leafs$left(chunkPos, ticket);
                }

                return removed;
            }, removedTickets);
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

    @Inject(method = "shouldKeepDimensionActive", at = @At("HEAD"), cancellable = true)
    private void leafs$countedActivityRead(CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(leafs$keepingActive > 0);
    }

    @WrapMethod(method = "hasTickets")
    private boolean leafs$monitoredEmptinessRead(Operation<Boolean> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @Unique
    private void leafs$entered(long key, Ticket ticket) {
        if (ticket.getType().hasTimeout()) {
            leafs$timeouts.track(key, ticket);
        }

        if (ticket.getType().shouldKeepDimensionActive()) {
            leafs$keepingActive++;
        }
    }

    @Unique
    private void leafs$left(long key, Ticket ticket) {
        if (ticket.getType().hasTimeout()) {
            leafs$timeouts.untrack(key, ticket);
        }

        if (ticket.getType().shouldKeepDimensionActive()) {
            leafs$keepingActive--;
        }
    }
}
