package fr.hardel.leafs.chunk.loader;

import fr.hardel.leafs.chunk.LeafsTicketTypes;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;

/**
 * The shared refcount table over the per-player view stages. Vanilla dedupes tickets by type and
 * level, so two players' tickets on the same chunk would collapse into one and the first leaver
 * would strip the other; this table materialises at most one real ticket per chunk and stage, on the
 * 0-to-1 and 1-to-0 transitions. A release swaps to a delayed ticket that expires on its own, which
 * keeps a player walking along a border from unloading and reloading the same chunks every tick.
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

    public static int level(int stage) {
        return switch (stage) {
            case LOADED -> ChunkLevel.MAX_LEVEL;
            case GENERATED -> ChunkLevel.byStatus(FullChunkStatus.FULL);
            case TICK -> ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
            default -> throw new IllegalArgumentException("Unknown view stage " + stage);
        };
    }

    private static TicketType type(int stage) {
        return stage == TICK ? LeafsTicketTypes.viewTick : LeafsTicketTypes.view;
    }

    private static TicketType delayedType(int stage) {
        return stage == TICK ? LeafsTicketTypes.viewTickDelayed : LeafsTicketTypes.viewDelayed;
    }
}
