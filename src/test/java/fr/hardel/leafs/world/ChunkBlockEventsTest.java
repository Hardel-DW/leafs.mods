package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkBlockEventsTest {

    private static BlockEventData at(int x) {
        return new BlockEventData(new BlockPos(x, 64, 0), null, 0, 0);
    }

    @Test
    void drainHandsEventsOverWithTheirSequenceAndEmptiesTheChunk() {
        ChunkBlockEvents events = new ChunkBlockEvents();
        events.add(at(1), 7);
        events.add(at(2), 3);
        List<String> drained = new ArrayList<>();

        events.drainTo((event, sequence) -> drained.add(event.pos().getX() + "@" + sequence));

        assertEquals(List.of("1@7", "2@3"), drained, "posting order inside the chunk, the sequence decides across chunks");
        assertTrue(events.isEmpty());
    }

    @Test
    void aDuplicateEventCollapsesOntoTheFirst() {
        ChunkBlockEvents events = new ChunkBlockEvents();
        events.add(at(1), 7);
        events.add(at(1), 9);
        List<Long> sequences = new ArrayList<>();

        events.drainTo((event, sequence) -> sequences.add(sequence));

        assertEquals(List.of(7L), sequences);
    }

    @Test
    void clearAreaDropsWhatTheBoxCovers() {
        ChunkBlockEvents events = new ChunkBlockEvents();
        events.add(at(1), 1);
        events.add(at(10), 2);

        events.clearArea(new BoundingBox(0, 0, 0, 5, 128, 5));
        List<Integer> left = new ArrayList<>();
        events.drainTo((event, sequence) -> left.add(event.pos().getX()));

        assertEquals(List.of(10), left);
    }
}
