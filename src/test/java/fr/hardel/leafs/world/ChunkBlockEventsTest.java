package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Vanilla's block event set replayed per region: level-wide order across chunks, and an event leaves its set only as it runs. */
class ChunkBlockEventsTest {

    private static BlockEventData at(int x) {
        return new BlockEventData(new BlockPos(x, 64, 0), null, 0, 0);
    }

    private static List<Integer> run(List<ChunkBlockEvents> sets) {
        List<Integer> ran = new ArrayList<>();
        ChunkBlockEvents.runAll(sets, event -> ran.add(event.pos().getX()));
        return ran;
    }

    @Test
    void eventsReplayInPostingOrderAcrossChunksAndCascadesRunInTheSamePass() {
        ChunkBlockEvents first = new ChunkBlockEvents();
        ChunkBlockEvents second = new ChunkBlockEvents();
        first.add(at(1), 5);
        second.add(at(2), 3);
        List<Integer> ran = new ArrayList<>();

        ChunkBlockEvents.runAll(List.of(first, second), event -> {
            ran.add(event.pos().getX());
            if (event.pos().getX() == 1) {
                second.add(at(3), 9);
            }
        });

        assertEquals(List.of(2, 1, 3), ran, "level-wide sequence first, the cascade posted while running lands in the next pass");
    }

    @Test
    void aDuplicateEventCollapsesOntoTheFirst() {
        ChunkBlockEvents events = new ChunkBlockEvents();
        events.add(at(1), 7);
        events.add(at(1), 9);

        assertEquals(List.of(1), run(List.of(events)));
    }

    /** Vanilla removes one event at a time, so an event still pending collapses onto itself when re-posted. */
    @Test
    void anEventRePostedWhileAnEarlierOneRunsRunsOnce() {
        ChunkBlockEvents chunk = new ChunkBlockEvents();
        chunk.add(at(1), 1);
        chunk.add(at(2), 2);
        List<Integer> ran = new ArrayList<>();

        ChunkBlockEvents.runAll(List.of(chunk), event -> {
            ran.add(event.pos().getX());
            if (event.pos().getX() == 1) {
                chunk.add(at(2), 3);
            }
        });

        assertEquals(List.of(1, 2), ran);
    }

    @Test
    void anEventClearedByAnEarlierOneDoesNotRun() {
        ChunkBlockEvents chunk = new ChunkBlockEvents();
        chunk.add(at(1), 1);
        chunk.add(at(10), 2);
        List<Integer> ran = new ArrayList<>();

        ChunkBlockEvents.runAll(List.of(chunk), event -> {
            ran.add(event.pos().getX());
            if (event.pos().getX() == 1) {
                chunk.removeInside(new BoundingBox(8, 0, 0, 12, 128, 5));
            }
        });

        assertEquals(List.of(1), ran);
    }

    /** Vanilla removes one event at a time, so an event cleared then posted again goes to the end of the set. */
    @Test
    void anEventClearedThenPostedAgainRunsLast() {
        ChunkBlockEvents chunk = new ChunkBlockEvents();
        chunk.add(at(1), 1);
        chunk.add(at(10), 2);
        chunk.add(at(3), 3);
        List<Integer> ran = new ArrayList<>();

        ChunkBlockEvents.runAll(List.of(chunk), event -> {
            ran.add(event.pos().getX());
            if (event.pos().getX() == 1) {
                chunk.removeInside(new BoundingBox(8, 0, 0, 12, 128, 5));
                chunk.add(at(4), 4);
                chunk.add(at(10), 5);
            }
        });

        assertEquals(List.of(1, 3, 4, 10), ran);
    }

    @Test
    void aSetLeftOutOfThePassKeepsItsEvents() {
        ChunkBlockEvents idle = new ChunkBlockEvents();
        idle.add(at(1), 1);

        assertEquals(List.of(), run(List.of()));
        assertEquals(List.of(1), run(List.of(idle)));
    }

    @Test
    void removeInsideDropsWhatTheBoxCovers() {
        ChunkBlockEvents events = new ChunkBlockEvents();
        events.add(at(1), 1);
        events.add(at(10), 2);

        events.removeInside(new BoundingBox(0, 0, 0, 5, 128, 5));

        assertEquals(List.of(10), run(List.of(events)));
    }
}
