package fr.hardel.leafs.chunk;

import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

/** A region decides the unload of its own chunks in its tick; the server thread decides for everyone only at shutdown. */
public final class ChunkUnloads {
    private final ChunkMap chunkMap;
    private final LongSet toDrop;
    private final LevelRegions regions;
    private final MinuteCounter unloads;

    public ChunkUnloads(ChunkMap chunkMap, LongSet toDrop, LevelRegions regions, MinuteCounter unloads) {
        this.chunkMap = chunkMap;
        this.toDrop = toDrop;
        this.regions = regions;
        this.unloads = unloads;
    }

    public void decideFor(Region<RegionTickData> region) {
        for (LongIterator iterator = toDrop.iterator(); iterator.hasNext(); ) {
            long pos = iterator.nextLong();
            if (region.owns(ChunkPos.getX(pos), ChunkPos.getZ(pos))) {
                iterator.remove();
                claim(pos);
            }
        }
    }

    public void decideAll() {
        for (LongIterator iterator = toDrop.iterator(); iterator.hasNext(); ) {
            long pos = iterator.nextLong();
            iterator.remove();
            claim(pos);
        }
    }

    /** The teardown is scheduled while the chunk still has its owner, then the regionizer lets the chunk go. */
    private void claim(long pos) {
        ChunkHolder holder = RegionChunkAccess.scheduling(chunkMap).claimUnload(pos);
        if (holder == null) {
            return;
        }

        unloads.increment();
        ((ChunkUnloadAccess) chunkMap).leafs$scheduleUnload(pos, holder);
        regions.chunkHolderDestroyed(ChunkPos.getX(pos), ChunkPos.getZ(pos));
    }
}
