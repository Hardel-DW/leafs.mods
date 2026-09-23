package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Work;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.Visibility;

import java.util.function.LongPredicate;

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

    public void deliver(ChunkPos pos, Runnable delivery) {
        LevelChunks.of(level).owners().submit(pos.x(), pos.z(), Work.CHUNK, delivery);
    }

    public LongSet pendingUnloads() {
        return manager.leafs$chunksToUnload();
    }

    public void unloadHidden(LongPredicate owned) {
        manager.leafs$chunksToUnload().removeIf((long chunkKey) -> owned.test(chunkKey) && unload(chunkKey));
    }

    public void unloadHidden(long chunkKey) {
        if (unload(chunkKey)) {
            manager.leafs$chunksToUnload().remove(chunkKey);
        }
    }

    public void saveChunkOnOwner(long chunkKey) {
        if (manager.leafs$visibility(chunkKey) == Visibility.HIDDEN) {
            unload(chunkKey);
        } else {
            manager.leafs$storeChunk(chunkKey);
        }
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
