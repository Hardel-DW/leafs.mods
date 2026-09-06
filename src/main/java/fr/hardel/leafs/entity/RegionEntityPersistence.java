package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Work;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.Visibility;

import java.util.function.LongPredicate;

/** Entity persistence by owner: arrival, unload and autosave run on the owner of the chunk. Retries ride vanilla's chunksToUnload. */
public final class RegionEntityPersistence {
    private final ServerLevel level;
    private final EntityManagerAccess manager;
    private final Runnable inboxDrain;

    public RegionEntityPersistence(ServerLevel level, EntityManagerAccess manager, Runnable inboxDrain) {
        this.level = level;
        this.manager = manager;
        this.inboxDrain = inboxDrain;
    }

    public ServerLevel level() {
        return level;
    }

    /** A loaded entity chunk lands on its owner; an empty chunk completes on the requesting owner and runs in place. */
    public void deliver(ChunkPos pos, Runnable delivery) {
        LevelChunks.of(level).owners().submit(pos.x(), pos.z(), Work.CHUNK, delivery);
    }

    public LongSet pendingUnloads() {
        return manager.leafs$chunksToUnload();
    }

    /** Vanilla's processUnloads over the chunks the caller owns: a settled chunk leaves the set, a failed unload stays there for a later pass. */
    public void unloadHidden(LongPredicate owned) {
        manager.leafs$chunksToUnload().removeIf((long chunkKey) -> owned.test(chunkKey) && unload(chunkKey));
    }

    public void unloadHidden(long chunkKey) {
        if (unload(chunkKey)) {
            manager.leafs$chunksToUnload().remove(chunkKey);
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

        inboxDrain.run();
    }

    /** Settled means nothing more to do here: a chunk revived since its queueing, or one whose entities are gone. Entities still loading are not, the next pass retries. */
    private boolean unload(long chunkKey) {
        return manager.leafs$visibility(chunkKey) != Visibility.HIDDEN || manager.leafs$unloadChunk(chunkKey);
    }
}
