package fr.hardel.leafs.world;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkTickersTest {

    private static final class FakeTicker implements TickingBlockEntity {
        private final String name;
        private final List<String> ticked;
        private boolean removed;
        private boolean refuses;
        private Runnable onTick;

        private FakeTicker(String name, List<String> ticked) {
            this.name = name;
            this.ticked = ticked;
        }

        @Override
        public void tick() {
            if (refuses) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "neighbour chunk not present");
            }

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
    @Test
    void aRefusedBlockEntitySkipsItselfAndTheRestOfThePhaseTicks() {
        List<String> ticked = new ArrayList<>();
        ChunkTickers tickers = new ChunkTickers();
        FakeTicker border = new FakeTicker("border hopper", ticked);
        border.refuses = true;
        tickers.add(new FakeTicker("first", ticked));
        tickers.add(border);
        tickers.add(new FakeTicker("third", ticked));

        assertDoesNotThrow(() -> tickers.tickAll(true));

        assertEquals(List.of("first", "third"), ticked);
        assertEquals(3, tickers.size(), "a refusal removes nothing, the next tick retries");
    }
}
