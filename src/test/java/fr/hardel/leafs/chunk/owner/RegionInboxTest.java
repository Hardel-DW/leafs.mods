package fr.hardel.leafs.chunk.owner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionInboxTest {
    private final RegionInbox inbox = new RegionInbox();
    private final List<String> ran = new ArrayList<>();

    @Test
    void everythingPostedBeforeThePassRunsInOrder() {
        inbox.post(0, 0, () -> ran.add("first"));
        inbox.post(0, 0, () -> ran.add("second"));
        inbox.post(0, 0, () -> ran.add("third"));

        assertEquals(3, inbox.drain());
        assertEquals(List.of("first", "second", "third"), ran);
        assertEquals(0, inbox.size());
    }

    @Test
    void aTaskPostedDuringThePassWaitsForTheNextOne() {
        inbox.post(0, 0, () -> {
            ran.add("first");
            inbox.post(0, 0, () -> ran.add("late"));
        });
        inbox.post(0, 0, () -> ran.add("second"));

        assertEquals(2, inbox.drain());
        assertEquals(List.of("first", "second"), ran);
        assertEquals(1, inbox.drain());
        assertEquals(List.of("first", "second", "late"), ran);
    }

    /** 2026-09-05: a publication waited for a neighbour whose own publication sat behind it in the same pass, forever. */
    @Test
    void aTaskThatDrainsWhileRunningReachesWhatWasPostedAfterIt() {
        inbox.post(0, 0, () -> {
            ran.add("first");
            inbox.drain();
            ran.add("first done");
        });
        inbox.post(0, 0, () -> ran.add("second"));

        assertEquals(1, inbox.drain());
        assertEquals(List.of("first", "second", "first done"), ran);
        assertEquals(0, inbox.size());
    }
}
