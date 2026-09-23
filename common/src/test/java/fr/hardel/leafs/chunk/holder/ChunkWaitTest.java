package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.world.WorldTickContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkWaitTest {
    private final List<String> released = new ArrayList<>();

    /** 2026-09-06: the portal spiral re-read the same nether chunks, each unloaded as soon as its wait ended; vanilla keeps them until its tick ends. */
    @Test
    void aDemandLivesUntilTheTickEnds() {
        WorldTickContext tick = WorldTickContext.enter(null, null, null);
        tick.keep(() -> released.add("read"));
        tick.keep(() -> released.add("write"));
        assertEquals(List.of(), released);

        tick.exit();
        assertEquals(List.of("read", "write"), released);
    }

    @Test
    void aDemandLivesUntilTheBorrowEnds() {
        RegionBorrow borrow = RegionBorrow.enter();
        borrow.keep(() -> released.add("read"));
        assertEquals(List.of(), released);

        borrow.exit();
        assertEquals(List.of("read"), released);
    }
}
