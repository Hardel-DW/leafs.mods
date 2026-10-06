package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.owner.ChunkOwners;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;

public final class ChunkBroadcasts {
    private ChunkBroadcasts() {}

    /** Broadcasts the changed chunks the current thread holds, and leaves the others to their owner. */
    public static void held(Set<ChunkHolder> changed, ChunkOwners owners) {
        for (ChunkHolder holder : changed) {
            ChunkPos pos = holder.getPos();
            if (owners.holds(pos.x(), pos.z()) && changed.remove(holder)) {
                broadcast(holder);
            }
        }
    }

    public static void broadcast(ChunkHolder holder) {
        LevelChunk chunk = holder.hasChangesToBroadcast() ? holder.getTickingChunk() : null;
        if (chunk != null) {
            holder.broadcastChanges(chunk);
        }
    }
}
