package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;

/** Vanilla's broadcast pass over the holders an owner walks: the changed ones send their blocks and light to their players. */
public final class ChunkBroadcasts {

    private ChunkBroadcasts() {
    }

    public static void changed(Iterable<ChunkHolder> holders) {
        for (ChunkHolder holder : holders) {
            LevelChunk chunk = holder.hasChangesToBroadcast() ? holder.getTickingChunk() : null;
            if (chunk != null) {
                holder.broadcastChanges(chunk);
            }
        }
    }
}
