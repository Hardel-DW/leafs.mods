package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.TickBarrier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-08-20: a promotion effect run inline under the drain's area locks posted a ticket into the storage monitor a worker held while waiting on a cell, watchdog kill. */
class ChunkSchedulingTest {

    @Test
    void anOwnedEffectStagesUntilTheAreaReleases() {
        LevelRegions regions = new LevelRegions(LeafsConfig.defaults());
        TickBarrier barrier = new TickBarrier();
        ChunkScheduling scheduling = new ChunkScheduling(null, null, regions, barrier, () -> false, new DeferStats(), null);
        List<String> order = new ArrayList<>();

        barrier.raise();
        try {
            assertTrue(scheduling.isOwner(0, 0), "the barrier holder owns every position of every level");
            scheduling.runOnOwner(0, 0, () -> order.add("inline"));
            assertEquals(List.of("inline"), order, "outside any area, the owner still runs its effects on the spot");

            order.clear();
            scheduling.mutateArea(0, 0, 1, () -> {
                scheduling.runOnOwner(0, 0, () -> order.add("effect"));
                order.add("underTheArea");
            });
        } finally {
            barrier.drop();
        }

        assertEquals(List.of("underTheArea", "effect"), order);
    }
}
