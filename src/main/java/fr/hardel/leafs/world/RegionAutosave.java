package fr.hardel.leafs.world;

import fr.hardel.leafs.entity.RegionEntityData;
import fr.hardel.leafs.entity.RegionEntityPersistence;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.region.Region;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;

/**
 * Region-side autosave, driven by the level's epoch. The global trigger only bumps the epoch; each
 * region notices it on its own tick, saves its players, snapshots its chunks and walks a few per
 * tick: the terrain snapshot at vanilla's per-chunk cadence, the entity chunk stored like vanilla's
 * entity autosave. Saving twice is harmless and skipping is the only real bug, so a shape change
 * rewalks: a merge keeps done-ness only when both parts had finished, a split's children inherit it
 * only from a parent that had finished.
 */
public final class RegionAutosave {
    private static final int CHUNKS_PER_TICK = 20;

    private long savedEpoch;
    private long walkingEpoch;
    private final LongArrayList backlog = new LongArrayList();

    /** Runs while TICKING on the owner, where the chunk iteration and the tick list are legal. */
    public void tick(ServerLevel level, Region<?> region, RegionEntityData entityData, long epoch) {
        if (epoch == savedEpoch) {
            return;
        }

        if (walkingEpoch != epoch) {
            begin(level, region, entityData, epoch);
        }

        drain(level);
        if (backlog.isEmpty()) {
            savedEpoch = epoch;
            walkingEpoch = 0;
        }
    }

    private void begin(ServerLevel level, Region<?> region, RegionEntityData entityData, long epoch) {
        walkingEpoch = epoch;
        backlog.clear();
        region.forEachChunk((chunkX, chunkZ) -> backlog.add(ChunkPos.pack(chunkX, chunkZ)));
        entityData.tickList().forEach(entity -> {
            if (entity instanceof ServerPlayer player) {
                level.getServer().getPlayerList().save(player);
            }
        });
    }

    private void drain(ServerLevel level) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        RegionEntityPersistence persistence = ((ServerLevelEntityAccess) level).leafs$entityPersistence();
        long now = Util.getMillis();
        for (int walked = 0; walked < CHUNKS_PER_TICK && !backlog.isEmpty(); walked++) {
            long chunkKey = backlog.popLong();
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkKey);
            if (holder != null) {
                chunkMap.saveChunkIfNeeded(holder, now);
            }

            if (persistence != null) {
                persistence.saveChunkOnOwner(chunkKey);
            }
        }
    }

    /** Merge target: the union rewalks unless both parts had finished the epoch. */
    public void absorb(RegionAutosave other) {
        savedEpoch = Math.min(savedEpoch, other.savedEpoch);
        walkingEpoch = 0;
        backlog.clear();
    }

    /** Split child: a mid-walk parent leaves the child unsaved, so the child rewalks its share. */
    public void inheritFrom(RegionAutosave parent) {
        if (parent.walkingEpoch == 0) {
            savedEpoch = parent.savedEpoch;
        }
    }
}
