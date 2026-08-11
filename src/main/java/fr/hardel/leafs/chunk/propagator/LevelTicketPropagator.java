package fr.hardel.leafs.chunk.propagator;

import fr.hardel.leafs.chunk.core.ChunkScheduling;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import net.minecraft.world.level.ChunkPos;

/**
 * The level authority for chunk tickets: fed inline by the ticket listener under the table monitor,
 * drained by any thread that staged work. Each drained section reports its level batch to the
 * scheduling layer under the ticket area, and starts the generation tasks the batch built once the
 * locks are released. Its shadow ran six hours at 550 players against vanilla without one disagreement.
 */
public final class LevelTicketPropagator extends LeafsTicketPropagator {

    private final AreaLock ticketLock = new AreaLock(SECTION_SHIFT);
    private volatile ChunkScheduling scheduling;

    /** Wired at ChunkMap construction, before the ticket storage binds its level and the first feed can arrive. */
    public void bindScheduling(ChunkScheduling scheduling) {
        this.scheduling = scheduling;
    }

    public ChunkScheduling scheduling() {
        ChunkScheduling bound = scheduling;
        if (bound == null) {
            throw new IllegalStateException("Chunk scheduling requested before the level's ChunkMap bound it");
        }

        return bound;
    }

    /** No table read in here: the level rides the listener call, so this never waits on the table monitor. */
    public void feed(long pos, int newLevel) {
        int inverted = convertBetweenTicketLevels(newLevel);
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);
        AreaLock.Node node = ticketLock.lock(chunkX, chunkZ, 0);
        try {
            if (inverted <= 0) {
                removeSource(chunkX, chunkZ);
            } else {
                setSource(chunkX, chunkZ, Math.min(inverted, MAX_SOURCE_LEVEL));
            }
        } finally {
            ticketLock.unlock(node);
        }
    }

    /** Any thread may drain; far apart sections drain in parallel, neighbouring ones serialize on the ticket area. */
    public void drain() {
        performUpdates(ticketLock);
    }

    @Override
    protected void onLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
        scheduling().applyLevelUpdates(updates);
    }

    @Override
    protected void onSectionDrained() {
        scheduling().startCollectedTasks();
    }
}
