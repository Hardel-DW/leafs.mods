package fr.hardel.leafs.chunk.ticket;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongPredicate;

public final class TicketTimeoutIndex {

    private record TrackedTicket(long chunkPos, Ticket ticket) {
        boolean matches(long chunkPos, Ticket ticket) {
            return this.chunkPos == chunkPos && this.ticket.getType() == ticket.getType() && this.ticket.getTicketLevel() == ticket.getTicketLevel();
        }
    }

    private final TicketStorage storage;
    private final TicketGraphs graphs;
    private final int sectionShift;
    private final ConcurrentLong2ObjectMap<ConcurrentLinkedQueue<TrackedTicket>> sections = new ConcurrentLong2ObjectMap<>();
    private volatile LongPredicate busy = _ -> false;

    public TicketTimeoutIndex(TicketStorage storage, TicketGraphs graphs, int sectionShift) {
        this.storage = storage;
        this.graphs = graphs;
        this.sectionShift = sectionShift;
    }

    public void pauseWhile(LongPredicate busy) {
        this.busy = busy;
    }

    public void track(long chunkPos, Ticket ticket) {
        sections.compute(sectionOf(chunkPos), (_, queue) -> {
            ConcurrentLinkedQueue<TrackedTicket> target = queue == null ? new ConcurrentLinkedQueue<>() : queue;
            target.add(new TrackedTicket(chunkPos, ticket));
            return target;
        });
    }

    public void untrack(long chunkPos, Ticket ticket) {
        sections.compute(sectionOf(chunkPos), (_, queue) -> {
            if (queue == null) {
                return null;
            }

            queue.removeIf(tracked -> tracked.matches(chunkPos, ticket));
            return queue.isEmpty() ? null : queue;
        });
    }

    public void purgeSections(long[] sectionKeys) {
        for (long key : sectionKeys) {
            purgeSection(key);
        }
    }

    public void purgeUnowned(LongPredicate sectionOwned) {
        for (long key : sections.keySet()) {
            if (!sectionOwned.test(key)) {
                purgeSection(key);
            }
        }
    }

    private void purgeSection(long sectionKey) {
        ConcurrentLinkedQueue<TrackedTicket> queue = sections.get(sectionKey);
        if (queue == null) {
            return;
        }

        graphs.batch(() -> {
            synchronized (storage) {
                countDown(queue);
            }
        });
    }

    private void countDown(Iterable<TrackedTicket> queue) {
        for (TrackedTicket tracked : queue) {
            if (!canExpire(tracked.ticket(), tracked.chunkPos())) {
                continue;
            }

            tracked.ticket().decreaseTicksLeft();
            if (tracked.ticket().isTimedOut()) {
                storage.removeTicket(tracked.chunkPos(), tracked.ticket());
            }
        }
    }

    private boolean canExpire(Ticket ticket, long chunkPos) {
        return ticket.getType().canExpireIfUnloaded() || !busy.test(chunkPos);
    }

    private long sectionOf(long chunkPos) {
        return CoordinateKey.pack(ChunkPos.getX(chunkPos) >> sectionShift, ChunkPos.getZ(chunkPos) >> sectionShift);
    }
}
