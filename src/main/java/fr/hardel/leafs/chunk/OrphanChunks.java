package fr.hardel.leafs.chunk;

import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.world.ChunkSaves;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;

/** The loaded chunks no region owns, the view distance beyond every ring: the server thread keeps their mail, their saves and their hidden entity chunks. */
public final class OrphanChunks {
    private final ServerLevel level;
    private final Regionizer<RegionTickData> regionizer;
    private final ChunkMailbox mailbox;
    private final ChunkSaves saves;
    private final LongArrayList epochBacklog = new LongArrayList();
    private long epoch;

    public OrphanChunks(ServerLevel level, Regionizer<RegionTickData> regionizer, ChunkMailbox mailbox) {
        this.level = level;
        this.regionizer = regionizer;
        this.mailbox = mailbox;
        this.saves = new ChunkSaves(level);
    }

    public boolean owns(long chunkKey) {
        return regionizer.regionAt(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) == null;
    }

    /** The serial pass, once per level tick. */
    public void tick() {
        mailbox.drainOrphans(this::owns);
        ((ServerLevelEntityAccess) level).leafs$entityPersistence().unloadHidden(this::owns);
        saves.saveEagerly(this::owns);
        saveBacklog(ChunkSaves.CHUNKS_PER_TICK);
    }

    /** The epoch bump snapshots the orphans of the moment; a chunk a region adopts meanwhile is that region's to save. A forced epoch saves them all now. */
    public void beginEpoch(Collection<ChunkHolder> holders, long epoch, boolean forced) {
        this.epoch = epoch;
        epochBacklog.clear();
        for (ChunkHolder holder : holders) {
            long chunkKey = holder.getPos().pack();
            if (owns(chunkKey)) {
                epochBacklog.add(chunkKey);
            }
        }

        if (forced) {
            saveBacklog(Integer.MAX_VALUE);
        }
    }

    private void saveBacklog(int budget) {
        while (budget > 0 && !epochBacklog.isEmpty()) {
            long chunkKey = epochBacklog.popLong();
            ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunkKey);
            if (holder != null && owns(chunkKey) && saves.saveBehindEpoch(holder, epoch)) {
                budget--;
            }
        }
    }
}
