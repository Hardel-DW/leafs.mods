package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockEventData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 2026-08-29: one batch per level was shared by every region ticking in parallel; the batch now belongs to the region's world data. */
class BlockEventBatchTest {

    private static BlockEventData at(int x) {
        return new BlockEventData(new BlockPos(x, 64, 0), null, 0, 0);
    }

    @Test
    void eventsReplayInPostingOrderAcrossChunksAndCascadesRunInTheSamePass() {
        ChunkBlockEvents first = new ChunkBlockEvents();
        ChunkBlockEvents second = new ChunkBlockEvents();
        first.add(at(1), 5);
        second.add(at(2), 3);
        BlockEventBatch<ChunkBlockEvents> batch = new BlockEventBatch<>(chunk -> chunk);
        List<Integer> ran = new ArrayList<>();

        batch.run(List.of(first, second), _ -> true, event -> {
            ran.add(event.pos().getX());
            if (event.pos().getX() == 1) {
                second.add(at(3), 9);
            }
        });

        assertEquals(List.of(2, 1, 3), ran, "level-wide sequence first, the cascade posted while running lands in the next pass");
    }

    @Test
    void aChunkThatDoesNotTickKeepsItsEvents() {
        ChunkBlockEvents idle = new ChunkBlockEvents();
        idle.add(at(1), 1);
        List<Integer> ran = new ArrayList<>();

        new BlockEventBatch<ChunkBlockEvents>(chunk -> chunk).run(List.of(idle), _ -> false, event -> ran.add(event.pos().getX()));

        assertEquals(List.of(), ran);
        assertEquals(false, idle.isEmpty());
    }
}
