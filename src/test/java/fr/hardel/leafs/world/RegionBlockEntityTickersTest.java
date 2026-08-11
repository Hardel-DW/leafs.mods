package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 2026-08-05 village crash: a Lithium-sleeping ticker answers a null position while staying
 * non-removed, so the region list keys on the chunk captured at registration.
 */
class RegionBlockEntityTickersTest {

    /** Lithium-shaped wrapper: the inner ticker is rebindable, asleep answers a null position. */
    private static final class SleepableTicker implements TickingBlockEntity {
        private final BlockPos pos;
        private boolean sleeping;
        private boolean removed;
        private int ticks;

        SleepableTicker(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public void tick() {
            if (!sleeping) {
                ticks++;
            }
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public BlockPos getPos() {
            return sleeping ? null : pos;
        }

        @Override
        public String getType() {
            return sleeping ? "<sleeping>" : "campfire";
        }
    }

    private static long chunkKeyOf(SleepableTicker ticker) {
        return ChunkPos.pack(ticker.pos);
    }

    @Test
    void sleepingTickerSurvivesTheTickPass() {
        RegionBlockEntityTickers tickers = new RegionBlockEntityTickers();
        SleepableTicker campfire = new SleepableTicker(new BlockPos(16, 64, 16));
        tickers.add(campfire, chunkKeyOf(campfire));
        campfire.sleeping = true;

        assertDoesNotThrow(() -> tickers.tickAll(true, chunkKey -> true));
        assertEquals(1, tickers.size());
    }

    @Test
    void wokenTickerTicksAgain() {
        RegionBlockEntityTickers tickers = new RegionBlockEntityTickers();
        SleepableTicker furnace = new SleepableTicker(new BlockPos(0, 64, 0));
        tickers.add(furnace, chunkKeyOf(furnace));
        furnace.sleeping = true;
        tickers.tickAll(true, chunkKey -> true);

        furnace.sleeping = false;
        tickers.tickAll(true, chunkKey -> true);

        assertEquals(1, furnace.ticks);
    }

    @Test
    void sleepingTickerKeepsItsBucketThroughASplit() {
        RegionBlockEntityTickers parent = new RegionBlockEntityTickers();
        SleepableTicker campfire = new SleepableTicker(new BlockPos(16, 64, 16));
        parent.add(campfire, chunkKeyOf(campfire));
        campfire.sleeping = true;

        RegionBlockEntityTickers child = new RegionBlockEntityTickers();
        assertDoesNotThrow(() -> parent.splitInto(0, sectionKey -> child));
        assertEquals(0, parent.size());
        assertEquals(1, child.size());
    }
}
