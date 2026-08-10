package fr.hardel.leafs.chunk.propagator;

import fr.hardel.leafs.Leafs;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.ChunkPos;

/**
 * The loading tracker's replacement (S4). Sources arrive through the ticket listener, whose level
 * argument is the new absolute loading level computed under the table monitor; staging takes the
 * chunk's ticket area cell, the propagator's contract against a concurrent drain. Updates land on
 * vanilla's own holder path, whose two update passes run right after the drain point. In shadow
 * mode (S5) the callback only compares against the holder levels vanilla computed and logs every
 * disagreement; nothing is written until the shadow has vouched for the algorithm.
 */
public final class LevelTicketPropagator extends LeafsTicketPropagator {
    private static final int UNLOADED = ChunkLevel.MAX_LEVEL + 1;

    private final DistanceManager distanceManager;
    private final AreaLock ticketLock = new AreaLock(SECTION_SHIFT);
    private final boolean shadow;
    private boolean shadowComparable;

    public LevelTicketPropagator(DistanceManager distanceManager, boolean shadow) {
        this.distanceManager = distanceManager;
        this.shadow = shadow;
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

    public void drain() {
        performUpdates(ticketLock);
    }

    /** A comparison is only honest once vanilla's budgeted graph emptied its queue this tick. */
    public void drainShadow(boolean vanillaConverged) {
        shadowComparable = vanillaConverged;
        performUpdates(ticketLock);
    }

    public boolean shadow() {
        return shadow;
    }

    @Override
    protected void onLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
        for (Long2ByteMap.Entry entry : updates.long2ByteEntrySet()) {
            long pos = entry.getLongKey();
            int level = convertBetweenTicketLevels(entry.getByteValue());
            ChunkHolder chunk = distanceManager.getChunk(pos);
            int holderLevel = chunk == null ? UNLOADED : chunk.getTicketLevel();
            if (shadow) {
                if (shadowComparable && holderLevel != level) {
                    Leafs.LOGGER.error("Propagator shadow disagreement at {}: vanilla {} leafs {}", ChunkPos.unpack(pos), holderLevel, level);
                }

                continue;
            }

            if (holderLevel != level) {
                chunk = distanceManager.updateChunkScheduling(pos, level, chunk, holderLevel);
                if (chunk != null) {
                    distanceManager.chunksToUpdateFutures.add(chunk);
                }
            }
        }
    }
}
