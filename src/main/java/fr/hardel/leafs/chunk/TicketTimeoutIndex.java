package fr.hardel.leafs.chunk;

import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongPredicate;

/** Only the tickets that can expire, sharded by region section. Each region purges its sections, the server thread the sections no region owns. */
public final class TicketTimeoutIndex {

    private record TrackedTicket(long chunkPos, Ticket ticket) {
    }

    private final TicketStorage storage;
    private final ChunkMap chunkMap;
    private final int sectionShift;
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<TrackedTicket>> sections = new ConcurrentHashMap<>();

    public TicketTimeoutIndex(TicketStorage storage, ChunkMap chunkMap, int sectionShift) {
        this.storage = storage;
        this.chunkMap = chunkMap;
        this.sectionShift = sectionShift;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /** Called under the storage monitor, for every timeout ticket the table actually stored. */
    public void track(long chunkPos, Ticket ticket) {
        long section = CoordinateKey.pack(ChunkPos.getX(chunkPos) >> sectionShift, ChunkPos.getZ(chunkPos) >> sectionShift);
        sections.compute(section, (key, queue) -> {
            ConcurrentLinkedQueue<TrackedTicket> target = queue == null ? new ConcurrentLinkedQueue<>() : queue;
            target.add(new TrackedTicket(chunkPos, ticket));
            return target;
        });
    }

    /** The owner's per-tick purge over its own sections; returns how many tickets expired. */
    public int purgeSections(long[] sectionKeys) {
        int expired = 0;
        for (long key : sectionKeys)
            expired += purgeSection(key);

        return expired;
    }

    /** The serial fallback for the sections no region owns. */
    public int purgeUnowned(LongPredicate sectionOwned) {
        int expired = 0;
        for (long key : sections.keySet())
            if (!sectionOwned.test(key))
                expired += purgeSection(key);

        return expired;
    }

    /** A ticket the table no longer holds leaves lazily; Ticket has no equals, so contains is an identity test. */
    private int purgeSection(long sectionKey) {
        ConcurrentLinkedQueue<TrackedTicket> queue = sections.get(sectionKey);
        if (queue == null)
            return 0;

        int expired = 0;
        for (Iterator<TrackedTicket> iterator = queue.iterator(); iterator.hasNext(); ) {
            TrackedTicket tracked = iterator.next();
            if (!storage.getTickets(tracked.chunkPos()).contains(tracked.ticket())) {
                iterator.remove();
                continue;
            }

            if (!canExpire(tracked.ticket(), tracked.chunkPos())) {
                continue;
            }

            tracked.ticket().decreaseTicksLeft();
            if (tracked.ticket().isTimedOut()) {
                storage.removeTicket(tracked.chunkPos(), tracked.ticket());
                iterator.remove();
                expired++;
            }
        }

        sections.compute(sectionKey, (key, remaining) -> remaining == null || remaining.isEmpty() ? null : remaining);
        return expired;
    }

    /** Vanilla {@code TicketStorage.canTicketExpire}: a busy chunk pauses the countdown of the types that must survive its save. */
    private boolean canExpire(Ticket ticket, long chunkPos) {
        if (ticket.getType().canExpireIfUnloaded())
            return true;

        ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(chunkPos);
        return holder == null || holder.isReadyForSaving();
    }
}
