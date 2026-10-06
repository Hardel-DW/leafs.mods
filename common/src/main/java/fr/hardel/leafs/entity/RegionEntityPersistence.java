package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Work;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.Visibility;


public final class RegionEntityPersistence {
    /** Runs a task on the owner of a chunk, after its current tick. */
    public interface Later {
        void run(long chunkKey, Runnable task);
    }

    private final ServerLevel level;
    private final EntityManagerAccess manager;
    private final Runnable inboxDrain;
    private final Later later;

    public RegionEntityPersistence(ServerLevel level, EntityManagerAccess manager, Runnable inboxDrain, Later later) {
        this.level = level;
        this.manager = manager;
        this.inboxDrain = inboxDrain;
        this.later = later;
    }

    public ServerLevel level() {
        return level;
    }

    public void deliver(ChunkPos pos, Runnable delivery) {
        LevelChunks.of(level).owners().submit(pos.x(), pos.z(), Work.CHUNK, delivery);
    }

    /** A chunk whose entities turned hidden hands their unload to its owner, who tries again next tick while the chunk still waits. */
    public void unloadHiddenLater(long chunkKey) {
        later.run(chunkKey, () -> {
            LongSet waiting = manager.leafs$chunksToUnload();
            if (waiting.contains(chunkKey) && !unload(chunkKey)) {
                unloadHiddenLater(chunkKey);
                return;
            }

            waiting.remove(chunkKey);
        });
    }

    public void saveChunkOnOwner(long chunkKey) {
        if (manager.leafs$visibility(chunkKey) == Visibility.HIDDEN) {
            unload(chunkKey);
            return;
        }

        manager.leafs$storeChunk(chunkKey);
    }

    public void drainPendingLoadsInline() {
        boolean hasMore = true;
        while (hasMore) {
            hasMore = level.getChunkSource().pollTask();
        }

        inboxDrain.run();
    }

    private boolean unload(long chunkKey) {
        return manager.leafs$visibility(chunkKey) != Visibility.HIDDEN || manager.leafs$unloadChunk(chunkKey);
    }
}
