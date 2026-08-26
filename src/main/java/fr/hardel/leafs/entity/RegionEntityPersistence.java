package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.PropagatorAccess;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.Visibility;

import java.util.function.LongPredicate;

/** Entity persistence by owner: arrival, unload and autosave run on the region owning the chunk. Retries ride vanilla's chunksToUnload. */
public final class RegionEntityPersistence {
    private final ServerLevel level;
    private final EntityManagerAccess manager;
    private final Runnable regionTaskDrain;

    public RegionEntityPersistence(ServerLevel level, EntityManagerAccess manager, Runnable regionTaskDrain) {
        this.level = level;
        this.manager = manager;
        this.regionTaskDrain = regionTaskDrain;
    }

    public ServerLevel level() {
        return level;
    }

    /** A loaded entity chunk lands on its owner; an empty chunk completes on the requesting owner and runs in place. */
    public void deliver(ChunkPos pos, Runnable delivery) {
        ((PropagatorAccess) level.getChunkSource().chunkMap.getDistanceManager()).leafs$propagator().scheduling().runOnOwner(pos.x(), pos.z(), delivery);
    }

    /** Vanilla's processUnloads over the chunks the caller owns: a chunk no longer hidden leaves the set, a hidden one unloads. */
    public void unloadHidden(LongPredicate owned) {
        for (LongIterator iterator = manager.leafs$chunksToUnload().iterator(); iterator.hasNext(); ) {
            long chunkKey = iterator.nextLong();
            if (owned.test(chunkKey)) {
                iterator.remove();
                unload(chunkKey);
            }
        }
    }

    /** The owning region's autosave walk: stores the entity chunk like vanilla's entity autosave, a HIDDEN one unloads instead. */
    public void saveChunkOnOwner(long chunkKey) {
        if (manager.leafs$visibility(chunkKey) == Visibility.HIDDEN) {
            unload(chunkKey);
        } else {
            manager.leafs$storeChunk(chunkKey);
        }
    }

    /** The saveAll wait loop spins on loads it requested itself; only the calling universal owner can run the routed deliveries. */
    public void drainPendingLoadsInline() {
        boolean hasMore = true;
        while (hasMore) {
            hasMore = level.getChunkSource().pollTask();
        }

        regionTaskDrain.run();
    }

    /** A chunk revived between dispatch and execution stays; the next HIDDEN transition re-queues it. */
    private void unload(long chunkKey) {
        if (manager.leafs$visibility(chunkKey) != Visibility.HIDDEN) {
            return;
        }

        if (!manager.leafs$unloadChunk(chunkKey)) {
            manager.leafs$requeueUnload(chunkKey);
        }
    }
}
