package fr.hardel.leafs.chunk.ticket;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongPredicate;

/** Only the tickets that can expire, sharded by region section: each region counts down its own, the workers' sweep the rest. The storage says when a ticket leaves. */
public final class TicketTimeoutIndex {

    private record TrackedTicket(long chunkPos, Ticket ticket) {
        boolean matches(long chunkPos, Ticket ticket) {
            return this.chunkPos == chunkPos && this.ticket.getType() == ticket.getType() && this.ticket.getTicketLevel() == ticket.getTicketLevel();
        }
    }

    private final TicketStorage storage;
    private final int sectionShift;
    private final ConcurrentLong2ObjectMap<ConcurrentLinkedQueue<TrackedTicket>> sections = new ConcurrentLong2ObjectMap<>();
    private volatile LongPredicate busy = _ -> false;

    public TicketTimeoutIndex(TicketStorage storage, int sectionShift) {
        this.storage = storage;
        this.sectionShift = sectionShift;
    }

    /** A chunk still generating or promoting pauses the countdown of the types that must survive its save. */
    public void pauseWhile(LongPredicate busy) {
        this.busy = busy;
    }

    /** Under the storage monitor, for every timeout ticket the table actually stored. */
    public void track(long chunkPos, Ticket ticket) {
        sections.compute(sectionOf(chunkPos), (_, queue) -> {
            ConcurrentLinkedQueue<TrackedTicket> target = queue == null ? new ConcurrentLinkedQueue<>() : queue;
            target.add(new TrackedTicket(chunkPos, ticket));
            return target;
        });
    }

    /** Under the storage monitor, for every ticket the table removed; vanilla matches removals by type and level, so does this. */
    public void untrack(long chunkPos, Ticket ticket) {
        sections.compute(sectionOf(chunkPos), (_, queue) -> {
            if (queue == null) {
                return null;
            }

            queue.removeIf(tracked -> tracked.matches(chunkPos, ticket));
            return queue.isEmpty() ? null : queue;
        });
    }

    /** The owner's per-tick countdown over its own sections; returns how many tickets expired. */
    public int purgeSections(long[] sectionKeys) {
        int expired = 0;
        for (long key : sectionKeys) {
            expired += purgeSection(key);
        }

        return expired;
    }

    /** The workers' countdown over the sections no region owns. */
    public int purgeUnowned(LongPredicate sectionOwned) {
        int expired = 0;
        for (long key : sections.keySet()) {
            if (!sectionOwned.test(key)) {
                expired += purgeSection(key);
            }
        }

        return expired;
    }

    /** An expired ticket leaves through the storage, whose removal untracks it here. */
    private int purgeSection(long sectionKey) {
        ConcurrentLinkedQueue<TrackedTicket> queue = sections.get(sectionKey);
        if (queue == null) {
            return 0;
        }

        int expired = 0;
        for (TrackedTicket tracked : queue) {
            if (!canExpire(tracked.ticket(), tracked.chunkPos())) {
                continue;
            }

            tracked.ticket().decreaseTicksLeft();
            if (tracked.ticket().isTimedOut() && storage.removeTicket(tracked.chunkPos(), tracked.ticket())) {
                expired++;
            }
        }

        return expired;
    }

    private boolean canExpire(Ticket ticket, long chunkPos) {
        return ticket.getType().canExpireIfUnloaded() || !busy.test(chunkPos);
    }

    private long sectionOf(long chunkPos) {
        return CoordinateKey.pack(ChunkPos.getX(chunkPos) >> sectionShift, ChunkPos.getZ(chunkPos) >> sectionShift);
    }
}
