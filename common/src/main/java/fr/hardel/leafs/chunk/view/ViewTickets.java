package fr.hardel.leafs.chunk.view;

import fr.hardel.leafs.chunk.level.ChunkLevels;
import fr.hardel.leafs.chunk.level.LevelListener;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;

import java.util.Arrays;
import java.util.function.LongPredicate;

/** The view of the players, ticketed nearest first. Only so many chunks load at once: the others wait with no ticket, so no holder, no task and no memory. */
public final class ViewTickets implements LevelListener {
    private static final int LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private final TicketStorage tickets;
    private final ChunkLevels players;
    private final LongPredicate full;
    private final int loads;
    private final LongLinkedOpenHashSet[] waiting = new LongLinkedOpenHashSet[ChunkMap.MAX_VIEW_DISTANCE + 2];
    private final LongOpenHashSet ticketed = new LongOpenHashSet();
    private final LongOpenHashSet loading = new LongOpenHashSet();
    private volatile int viewDistance;

    public ViewTickets(TicketStorage tickets, ChunkLevels players, int viewDistance, LongPredicate full, int loads) {
        this.tickets = tickets;
        this.players = players;
        this.viewDistance = viewDistance;
        this.full = full;
        this.loads = loads;
        Arrays.setAll(waiting, _ -> new LongLinkedOpenHashSet());
    }

    public int viewDistance() {
        return viewDistance;
    }

    public synchronized void viewDistance(int distance) {
        int previous = viewDistance;
        viewDistance = distance;
        players.forEachAtMost(Math.max(previous, distance), chunkKey -> {
            int level = players.level(chunkKey);
            if (level > previous) {
                waiting[level].add(chunkKey);
            }

            if (level > distance) {
                leave(chunkKey, level);
            }
        });
        admit();
    }

    @Override
    public synchronized void changed(long chunkKey, int oldLevel, int newLevel) {
        boolean saw = oldLevel <= viewDistance;
        boolean sees = newLevel <= viewDistance;
        if (saw && (!sees || !ticketed.contains(chunkKey))) {
            leave(chunkKey, oldLevel);
        }

        if (sees && !ticketed.contains(chunkKey)) {
            waiting[newLevel].add(chunkKey);
        }
    }

    @Override
    public synchronized void published(Runnable pass) {
        admit();
    }

    /** A chunk that turned FULL gives its place to the nearest one waiting. */
    public synchronized void arrived(long chunkKey) {
        if (loading.remove(chunkKey)) {
            admit();
        }
    }

    private void admit() {
        for (int distance = 0; distance <= viewDistance && loading.size() < loads; distance++) {
            LongLinkedOpenHashSet ring = waiting[distance];
            while (!ring.isEmpty() && loading.size() < loads) {
                long chunkKey = ring.removeFirstLong();
                ticketed.add(chunkKey);
                loading.add(chunkKey);
                tickets.addTicket(chunkKey, ticket());
                if (full.test(chunkKey)) {
                    loading.remove(chunkKey);
                }
            }
        }
    }

    private void leave(long chunkKey, int level) {
        if (!ticketed.remove(chunkKey)) {
            waiting[level].remove(chunkKey);
            return;
        }

        loading.remove(chunkKey);
        tickets.removeTicket(chunkKey, ticket());
    }

    private static Ticket ticket() {
        return new Ticket(TicketType.PLAYER_LOADING, LEVEL);
    }
}
