package fr.hardel.leafs.chunk;

import fr.hardel.leafs.metrics.MinuteCounter;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;

import java.util.function.LongPredicate;

/** A region decides the unload of its own chunks in its tick, the server thread decides for the chunks no region owns, and for everyone at shutdown. */
public final class ChunkUnloads {
    private final ChunkMap chunkMap;
    private final LongSet toDrop;
    private final MinuteCounter unloads;

    public ChunkUnloads(ChunkMap chunkMap, LongSet toDrop, MinuteCounter unloads) {
        this.chunkMap = chunkMap;
        this.toDrop = toDrop;
        this.unloads = unloads;
    }

    /** The caller's chunks among the ones vanilla marked to drop. */
    public void decide(LongPredicate owned) {
        for (LongIterator iterator = toDrop.iterator(); iterator.hasNext(); ) {
            long pos = iterator.nextLong();
            if (owned.test(pos)) {
                iterator.remove();
                claim(pos);
            }
        }
    }

    /** A chunk that unloads stopped simulating long before, so it left every region on its own. */
    private void claim(long pos) {
        ChunkHolder holder = RegionChunkAccess.scheduling(chunkMap).claimUnload(pos);
        if (holder == null) {
            return;
        }

        unloads.increment();
        ((ChunkUnloadAccess) chunkMap).leafs$scheduleUnload(pos, holder);
    }
}
