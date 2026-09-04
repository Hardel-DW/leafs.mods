package fr.hardel.leafs.chunk.owner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionInboxTest {
    private final RegionInbox inbox = new RegionInbox();
    private final List<String> ran = new ArrayList<>();

    @Test
    void aPassedDeadlineStillRunsOneTaskAndKeepsTheOrder() {
        inbox.post(0, 0, () -> ran.add("first"));
        inbox.post(0, 0, () -> ran.add("second"));
        inbox.post(0, 0, () -> ran.add("third"));

        assertEquals(1, inbox.drain(System.nanoTime() - 1));
        assertEquals(List.of("first"), ran);
        assertEquals(2, inbox.size());

        assertEquals(2, inbox.drain());
        assertEquals(List.of("first", "second", "third"), ran);
    }

    @Test
    void aTaskPostedDuringTheDrainWaitsBehindWhatWasKept() {
        inbox.post(0, 0, () -> {
            ran.add("first");
            inbox.post(0, 0, () -> ran.add("late"));
        });
        inbox.post(0, 0, () -> ran.add("second"));

        assertEquals(1, inbox.drain(System.nanoTime() - 1));
        assertEquals(2, inbox.drain());
        assertEquals(List.of("first", "second", "late"), ran);
    }
}
