package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// The demand sequence behind an ABSENT refusal: file the tickets, drain the propagator once so the holders exist, request the status so the pool delivers. Readiness completes at delivery.
public final class ChunkDemands {

    private ChunkDemands() {
    }

    // Null readiness when no holder accepted a request yet.
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
}
