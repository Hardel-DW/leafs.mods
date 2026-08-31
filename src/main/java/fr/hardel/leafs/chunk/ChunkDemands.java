package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The request behind a chunk wait: tickets, one drain so the holders exist, head of the generation queue, status request. The future completes at delivery. */
public final class ChunkDemands {
    private static final int HEAD_OF_QUEUE = 0;

    private ChunkDemands() {
    }

    /** Null when no holder accepted a request yet. */
    public static CompletableFuture<?> demand(ChunkMap chunkMap, ChunkStatus status, LongList positions) {
        TicketStorage tickets = chunkMap.level.getChunkSource().ticketStorage;
        for (int index = 0; index < positions.size(); index++) {
            tickets.addTicket(new Ticket(LeafsTicketTypes.demand, ChunkLevel.byStatus(status)), ChunkPos.unpack(positions.getLong(index)));
        }

        LevelTicketPropagator propagator = ((PropagatorAccess) chunkMap.getDistanceManager()).leafs$propagator();
        propagator.drain();

        List<CompletableFuture<?>> deliveries = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            long position = positions.getLong(index);
            ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(position);
            if (holder == null) {
                continue;
            }

            prioritise(chunkMap, holder);
            CompletableFuture<?> delivery = propagator.scheduling().requestStatus(ChunkPos.getX(position), ChunkPos.getZ(position), status);
            if (delivery != null) {
                deliveries.add(delivery);
            }
        }

        if (deliveries.isEmpty()) {
            return null;
        }

        return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new));
    }

    /** Someone waits for this chunk: its tasks move to the head of both dispatchers. The queue level is separate from the ticket level, nothing changes about loading. */
    private static void prioritise(ChunkMap chunkMap, ChunkHolder holder) {
        chunkMap.onLevelChange(holder.getPos(), holder::getQueueLevel, HEAD_OF_QUEUE, holder::setQueueLevel);
    }
}
