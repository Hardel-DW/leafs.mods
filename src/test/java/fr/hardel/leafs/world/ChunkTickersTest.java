package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkTickersTest {

    private static final class FakeTicker implements TickingBlockEntity {
        private final String name;
        private final List<String> ticked;
        private boolean removed;
        private Runnable onTick;

        private FakeTicker(String name, List<String> ticked) {
            this.name = name;
            this.ticked = ticked;
        }

        @Override
        public void tick() {
            ticked.add(name);
            if (onTick != null) {
                onTick.run();
            }
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public String getType() {
            return name;
        }
    }

    @Test
    void vanillaSemanticsRemovedOnesLeaveAndMidPassAddsWait() {
        List<String> ticked = new ArrayList<>();
        ChunkTickers tickers = new ChunkTickers();
        FakeTicker healthy = new FakeTicker("healthy", ticked);
        FakeTicker removed = new FakeTicker("removed", ticked);
        FakeTicker added = new FakeTicker("added", ticked);
        removed.removed = true;
        healthy.onTick = () -> tickers.add(added);
        tickers.add(healthy);
        tickers.add(removed);

        tickers.tickAll(true);
        assertEquals(List.of("healthy"), ticked);
        assertEquals(2, tickers.size(), "the removed ticker is gone, the pending one counted");

        healthy.onTick = null;
        tickers.tickAll(true);
        assertEquals(List.of("healthy", "healthy", "added"), ticked);
    }

    @Test
    void aFrozenTickRateOnlyPurges() {
        List<String> ticked = new ArrayList<>();
        ChunkTickers tickers = new ChunkTickers();
        tickers.add(new FakeTicker("healthy", ticked));

        tickers.tickAll(false);

        assertEquals(List.of(), ticked);
        assertEquals(1, tickers.size());
    }

    /** Hopper at the border: a refused block entity skips its own tick, the phase continues. */
}
