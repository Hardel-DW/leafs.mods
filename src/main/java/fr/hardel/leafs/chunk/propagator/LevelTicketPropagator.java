package fr.hardel.leafs.chunk.propagator;

import fr.hardel.leafs.Leafs;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.ChunkPos;

/**
 * The loading tracker's replacement (S4). Sources re-read the authoritative ticket table, updates
 * land on vanilla's own holder path, whose two update passes run right after the drain point. In
 * shadow mode (S5) the callback only compares against the holder levels vanilla computed and logs
 * every disagreement; nothing is written until the shadow has vouched for the algorithm.
 */
public final class LevelTicketPropagator extends LeafsTicketPropagator {
    private static final int UNLOADED = ChunkLevel.MAX_LEVEL + 1;

    private final DistanceManager distanceManager;
    private final AreaLock ticketLock = new AreaLock(SECTION_SHIFT);
    private final boolean shadow;

    public LevelTicketPropagator(DistanceManager distanceManager, boolean shadow) {
        this.distanceManager = distanceManager;
        this.shadow = shadow;
    }

    /** The listener is only a wake-up: the source level is re-read from the table, like vanilla's recompute does. */
    public void feed(long pos) {
        int inverted = convertBetweenTicketLevels(distanceManager.ticketStorage.getTicketLevelAt(pos, false));
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);
        if (inverted <= 0) {
            removeSource(chunkX, chunkZ);
        } else {
            setSource(chunkX, chunkZ, Math.min(inverted, MAX_SOURCE_LEVEL));
        }
    }

    public void drain() {
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
                if (holderLevel != level) {
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
