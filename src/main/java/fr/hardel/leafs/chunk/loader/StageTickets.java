package fr.hardel.leafs.chunk.loader;

import fr.hardel.leafs.chunk.LeafsTicketTypes;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;

/**
 * Refcounts the view stages into at most one real ticket per chunk and stage, because vanilla
 * dedupes tickets by type and level and the first leaver would strip every other player's. A release
 * swaps to a delayed ticket that expires on its own, against border-walking churn.
 */
public final class StageTickets {

    public static final int LOADED = 0;
    public static final int GENERATED = 1;
    public static final int TICK = 2;

    private final TicketStorage storage;
    private final Long2IntOpenHashMap[] counts = {new Long2IntOpenHashMap(), new Long2IntOpenHashMap(), new Long2IntOpenHashMap()};

    public StageTickets(TicketStorage storage) {
        this.storage = storage;
    }

    /** The new stage is acquired before the old one releases, so the aggregate level never dips between the two. */
    public void swap(long chunk, int from, int to) {
        acquire(chunk, to);
        release(chunk, from);
    }

    public synchronized void acquire(long chunk, int stage) {
        if (counts[stage].addTo(chunk, 1) == 0) {
            storage.addTicket(chunk, new Ticket(type(stage), level(stage)));
        }
    }

    /** The delayed ticket posts before the removal, so the aggregate level never dips below the stage. */
    public synchronized void release(long chunk, int stage) {
        Long2IntOpenHashMap stageCounts = counts[stage];
        int count = stageCounts.get(chunk);
        if (count <= 0) {
            throw new IllegalStateException("View stage " + stage + " released more often than acquired at " + chunk);
        }

        if (count == 1) {
            stageCounts.remove(chunk);
            storage.addTicket(chunk, new Ticket(delayedType(stage), level(stage)));
            storage.removeTicket(chunk, new Ticket(type(stage), level(stage)));
            return;
        }

        stageCounts.put(chunk, count - 1);
    }

    /** Vanilla only sends a chunk to its player from the ticking promotion, so both visible stages sit at this level and the ticket type alone carries the simulation axis. */
    private static final int VIEW_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

    static int level(int stage) {
        return stage == LOADED ? ChunkLevel.MAX_LEVEL : VIEW_LEVEL;
    }

    private static TicketType type(int stage) {
        return stage == TICK ? LeafsTicketTypes.viewTick : LeafsTicketTypes.view;
    }

    private static TicketType delayedType(int stage) {
        return stage == TICK ? LeafsTicketTypes.viewTickDelayed : LeafsTicketTypes.viewDelayed;
    }
}
