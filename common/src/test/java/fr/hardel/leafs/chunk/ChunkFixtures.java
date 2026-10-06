package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.chunk.pool.ChunkPlacement;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.Urgency;
import fr.hardel.leafs.global.GlobalScheduler;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

public final class ChunkFixtures {
    public static final class TestRegions implements ChunkOwners.Regions {
        private @Nullable RegionInbox inbox;
        private @Nullable Thread ticker;
        private boolean live = true;

        public TestRegions(@Nullable RegionInbox inbox) {
            this.inbox = inbox;
        }

        public void cover(@Nullable RegionInbox inbox) {
            this.inbox = inbox;
        }

        public void tickOn(@Nullable Thread ticker) {
            this.ticker = ticker;
        }

        @Override
        public void chunkChanged(int chunkX, int chunkZ) {
        }

        public void live(boolean live) {
            this.live = live;
        }

        @Override
        public boolean live() {
            return live;
        }

        @Override
        public @Nullable RegionInbox inboxAt(int chunkX, int chunkZ) {
            return inbox;
        }

        @Override
        public @Nullable Thread tickerAt(int chunkX, int chunkZ) {
            return ticker;
        }
    }

    private ChunkFixtures() {
    }

    public static ChunkPool pool(int threads) {
        return new ChunkPool(Thread.currentThread().getThreadGroup(), threads, ChunkPool.PRIORITIES, (_, _) -> { });
    }

    public static ChunkOwners owners(ChunkPool pool, ChunkOwners.Regions regions, ChunkOwners.Taker taker, GlobalScheduler server, Urgency urgency) {
        return new ChunkOwners(pool, new ChunkPlacement(pool, 0, urgency), regions, Thread.currentThread(), Runnable::run, taker, server);
    }

    public static CompoundTag photo(int dataVersion) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", dataVersion);
        return tag;
    }
}
