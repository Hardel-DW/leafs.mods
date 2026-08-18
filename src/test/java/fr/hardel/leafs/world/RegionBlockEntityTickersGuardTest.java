package fr.hardel.leafs.world;

import fr.hardel.leafs.ownership.OwnershipViolationException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hopper-at-the-border regression: a block entity whose neighbour chunk is absent refuses its
 * own tick and the phase continues, instead of the refusal escaping the region tick body and
 * halting the server.
 */
class RegionBlockEntityTickersGuardTest {

    private record FakeTicker(String name, List<String> ticked, boolean refuses) implements TickingBlockEntity {
        @Override
        public void tick() {
            if (refuses) {
                throw new OwnershipViolationException(OwnershipViolationException.Kind.ABSENT, "neighbour chunk not present");
            }

            ticked.add(name);
        }

        @Override
        public boolean isRemoved() {
            return false;
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
    void aRefusedBlockEntitySkipsItselfAndTheRestOfThePhaseTicks() {
        List<String> ticked = new ArrayList<>();
        RegionBlockEntityTickers tickers = new RegionBlockEntityTickers();
        tickers.add(new FakeTicker("first", ticked, false), 0);
        tickers.add(new FakeTicker("border hopper", ticked, true), 0);
        tickers.add(new FakeTicker("third", ticked, false), 0);

        assertDoesNotThrow(() -> tickers.tickAll(true, chunkKey -> true));

        assertEquals(List.of("first", "third"), ticked);
        assertEquals(3, tickers.size(), "a refusal removes nothing, the next tick retries");
    }
}
