package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.ticking.LevelRegions;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.Visibility;

/**
 * Region routing of the entity persistence pipeline. Arrival and unload execute on the region that
 * owns the chunk, because the add and remove callbacks feed the per-region tick lists and the NBT
 * work must not ride the global thread; the autosave store runs from each region's own epoch walk.
 * The serial phase only dispatches the unloads, and keeps the inline fallback for chunks no region
 * owns, which is legal there because it holds the exclusion. Retries ride the vanilla
 * chunksToUnload set, one truth source for what still has to leave.
 */
public final class RegionEntityPersistence {
    private final ServerLevel level;
    private final EntityManagerAccess manager;

    public RegionEntityPersistence(ServerLevel level, EntityManagerAccess manager) {
        this.level = level;
        this.manager = manager;
    }

    public ServerLevel level() {
        return level;
    }

    /** A loaded entity chunk lands on its owner; an empty chunk completes on the requesting owner and runs in place. */
    public void deliver(ChunkPos pos, Runnable delivery) {
        scheduling().runOnOwner(pos.x(), pos.z(), delivery);
    }

    /** The serial sweep of hidden chunks: each one is removed here and re-queued by its task if the store refuses. */
    public void sweepUnloads() {
        for (LongIterator iterator = manager.leafs$chunksToUnload().iterator(); iterator.hasNext(); ) {
            long chunkKey = iterator.nextLong();
            iterator.remove();
            if (manager.leafs$visibility(chunkKey) == Visibility.HIDDEN) {
                dispatch(chunkKey, () -> unload(chunkKey));
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

    /**
     * The saveAll wait loop: it spins on loads it requested itself, and with the pool possibly
     * stopped only the calling universal owner can run the routed deliveries. The chunk pump runs
     * first because deliveries issued before the level activated were queued there.
     */
    public void drainPendingLoadsInline() {
        boolean hasMore = true;
        while (hasMore) {
            hasMore = level.getChunkSource().pollTask();
        }

        LevelRegions.of(level).drainTasksInline();
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

    /** The owner is resolved at dispatch: entity visibility drops while the holder still exists, so it is usually alive. */
    private void dispatch(long chunkKey, Runnable task) {
        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        LevelRegions regions = LevelRegions.of(level);
        if (!regions.unloads().offer(regions.regionizer().regionAt(chunkX, chunkZ), chunkX, chunkZ, task)) {
            task.run();
        }
    }

    private ChunkScheduling scheduling() {
        return ((PropagatorAccess) level.getChunkSource().chunkMap.getDistanceManager()).leafs$propagator().scheduling();
    }
}
