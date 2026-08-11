package fr.hardel.leafs.chunk.propagator;

import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.ChunkPos;

/**
 * The level authority for chunk tickets: fed inline by the ticket listener under the table monitor,
 * drained at the serial drain point where it writes the holder levels vanilla's graph used to
 * compute. Its shadow ran six hours at 550 players against vanilla without one disagreement.
 */
public final class LevelTicketPropagator extends LeafsTicketPropagator {
    private static final int UNLOADED = ChunkLevel.MAX_LEVEL + 1;

    private final DistanceManager distanceManager;
    private final AreaLock ticketLock = new AreaLock(SECTION_SHIFT);

    public LevelTicketPropagator(DistanceManager distanceManager) {
        this.distanceManager = distanceManager;
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

    /** Serial drain point only: the holder scheduling written here stays single-threaded until the core chantier. */
    public void drain() {
        performUpdates(ticketLock);
    }

    @Override
    protected void onLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
        for (Long2ByteMap.Entry entry : updates.long2ByteEntrySet()) {
            long pos = entry.getLongKey();
            int level = convertBetweenTicketLevels(entry.getByteValue());
            ChunkHolder chunk = distanceManager.getChunk(pos);
            int holderLevel = chunk == null ? UNLOADED : chunk.getTicketLevel();
            if (holderLevel != level) {
                chunk = distanceManager.updateChunkScheduling(pos, level, chunk, holderLevel);
                if (chunk != null) {
                    distanceManager.chunksToUpdateFutures.add(chunk);
                }
            }
        }
    }
}
