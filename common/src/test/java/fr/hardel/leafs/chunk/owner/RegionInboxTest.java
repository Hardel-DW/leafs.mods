package fr.hardel.leafs.chunk.owner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionInboxTest {
    private final RegionInbox inbox = new RegionInbox(Long.MAX_VALUE);
    private final List<String> ran = new ArrayList<>();

    @Test
    void everythingPostedBeforeThePassRunsInOrder() {
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("first"));
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("second"));
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("third"));

        assertEquals(3, inbox.drain());
        assertEquals(List.of("first", "second", "third"), ran);
        assertEquals(0, inbox.size());
    }

    /** The region lost the section meanwhile: the task is not its work any more, it leaves through the owners instead of running here. */
    @Test
    void aTaskOnAChunkTheOwnerLostLeavesInsteadOfRunning() {
        List<RegionInbox.Posted> left = new ArrayList<>();
        RegionInbox owned = new RegionInbox(Long.MAX_VALUE, posted -> posted.chunkX() == 0, left::add);
        owned.post(0, 0, Work.GAME, () -> ran.add("mine"));
        owned.post(5, 0, Work.GAME, () -> ran.add("lost"));

        assertEquals(2, owned.drain());

        assertEquals(List.of("mine"), ran);
        assertEquals(1, left.size());
        assertEquals(5, left.getFirst().chunkX());
    }

    @Test
    void aTaskPostedDuringThePassWaitsForTheNextOne() {
        inbox.post(0, 0, Work.CHUNK, () -> {
            ran.add("first");
            inbox.post(0, 0, Work.CHUNK, () -> ran.add("late"));
        });
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("second"));

        assertEquals(2, inbox.drain());
        assertEquals(List.of("first", "second"), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("first", "second", "late"), ran);
    }

    @Test
    void aPassedDeadlineLeavesThePassForTheNextTick() {
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("first"));
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("second"));

        assertEquals(0, inbox.drain(System.nanoTime() - 1));
        assertEquals(2, inbox.size());
        assertEquals(2, inbox.drain(Long.MAX_VALUE));
        assertEquals(List.of("first", "second"), ran);
    }

    /** 2026-09-06: two block writes posted in order ran last to first, the second nested inside the wait of the first, and erased a fresh nether portal. */
    @Test
    void gameWorkWaitsItsTurnWhileChunkWorkRunsInsideAWait() {
        inbox.post(0, 0, Work.GAME, () -> {
            ran.add("write air");
            inbox.drainChunkWork();
            ran.add("air written");
        });
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("publication"));
        inbox.post(0, 0, Work.GAME, () -> ran.add("write portal"));

        assertEquals(1, inbox.drainChunkWork());
        assertEquals(List.of("publication"), ran);
        assertEquals(2, inbox.size());

        assertEquals(2, inbox.drain());
        assertEquals(List.of("publication", "write air", "air written", "write portal"), ran);
    }

    @Test
    void aPassRunsTheChunkWorkBeforeTheGameWork() {
        inbox.post(0, 0, Work.GAME, () -> ran.add("write"));
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("publication"));

        assertEquals(2, inbox.drain());
        assertEquals(List.of("publication", "write"), ran);
    }

    /** 2026-09-05: a publication waited for a neighbour whose own publication sat behind it in the same pass, forever. */

    @Test
    void aTaskThatDrainsWhileRunningReachesWhatWasPostedAfterIt() {
        inbox.post(0, 0, Work.CHUNK, () -> {
            ran.add("first");
            inbox.drainChunkWork();
            ran.add("first done");
        });
        inbox.post(0, 0, Work.CHUNK, () -> ran.add("second"));

        assertEquals(1, inbox.drain());
        assertEquals(List.of("first", "second", "first done"), ran);
        assertEquals(0, inbox.size());
    }
}
