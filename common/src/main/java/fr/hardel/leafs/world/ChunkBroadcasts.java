package fr.hardel.leafs.world;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;

public final class ChunkBroadcasts {
    private ChunkBroadcasts() {}

    public static void changed(Set<ChunkHolder> changed, Iterable<ChunkHolder> holders) {
        for (ChunkHolder holder : holders) {
            changed.remove(holder);
            LevelChunk chunk = holder.hasChangesToBroadcast() ? holder.getTickingChunk() : null;
            if (chunk != null) {
                holder.broadcastChanges(chunk);
            }
        }
    }
}
