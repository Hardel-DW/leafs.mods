package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.pool.ChunkPlacement;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.Urgency;
import fr.hardel.leafs.global.GlobalScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkTaskPriorityQueue;

public final class ChunkFixtures {
    private ChunkFixtures() {
    }

    public static ChunkPool pool(int threads) {
        return new ChunkPool(Thread.currentThread().getThreadGroup(), threads, ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT, (_, _) -> { });
    }

    public static ChunkOwners owners(ChunkPool pool, ChunkOwners.Inboxes inboxes, ChunkOwners.Ownership ownership, ChunkOwners.Taker taker, GlobalScheduler server, Urgency urgency) {
        return new ChunkOwners(pool, new ChunkPlacement(pool, 0, urgency), inboxes, ownership, () -> true, Runnable::run, taker, server);
    }

    public static CompoundTag photo(int dataVersion) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", dataVersion);
        return tag;
    }
}
