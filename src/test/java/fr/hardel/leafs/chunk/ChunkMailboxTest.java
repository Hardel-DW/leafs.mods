package fr.hardel.leafs.chunk;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkMailboxTest {

    /** Records the hold tickets as "+x,z" and "-x,z" in order. */
    private static final class RecordingHolds implements ChunkHoldController {
        final List<String> tickets = new ArrayList<>();

        @Override
        public void addHold(int chunkX, int chunkZ) {
            tickets.add("+" + chunkX + "," + chunkZ);
        }

        @Override
        public void removeHold(int chunkX, int chunkZ) {
            tickets.add("-" + chunkX + "," + chunkZ);
        }
    }

    @Test
    void aChunkIsHeldOncePerChunkUntilItsLastMailRan() {
        RecordingHolds holds = new RecordingHolds();
        ChunkMailbox mailbox = new ChunkMailbox(holds);
        List<String> ran = new ArrayList<>();
        mailbox.post(3, 4, () -> ran.add("first"));
        mailbox.post(3, 4, () -> ran.add("second"));
        mailbox.post(9, 9, () -> ran.add("elsewhere"));

        assertEquals(List.of("+3,4", "+9,9"), holds.tickets, "vanilla dedupes tickets, one hold per chunk");
        assertEquals(3, mailbox.size());
        assertEquals(3, mailbox.drainAll());

        assertEquals(List.of("first", "second"), ran.stream().filter(name -> !name.equals("elsewhere")).toList(), "FIFO inside a chunk");
        assertEquals(Set.of("+3,4", "+9,9", "-3,4", "-9,9"), Set.copyOf(holds.tickets));
        assertEquals(0, mailbox.size());
    }

    @Test
    void aRepostToTheSameChunkWaitsForTheNextPass() {
        ChunkMailbox mailbox = new ChunkMailbox(new RecordingHolds());
        List<String> ran = new ArrayList<>();
        mailbox.post(0, 0, () -> {
            ran.add("first");
            mailbox.post(0, 0, () -> ran.add("again"));
        });

        assertEquals(1, mailbox.drainAll());
        assertEquals(List.of("first"), ran);
        assertEquals(1, mailbox.drainAll());
        assertEquals(List.of("first", "again"), ran);
    }

    @Test
    void aThrowingMailLeavesTheRestQueuedWithTheHoldIntact() {
        RecordingHolds holds = new RecordingHolds();
        ChunkMailbox mailbox = new ChunkMailbox(holds);
        List<String> ran = new ArrayList<>();
        mailbox.post(1, 1, () -> {
            throw new IllegalStateException("region crash");
        });
        mailbox.post(1, 1, () -> ran.add("survivor"));

        assertThrows(IllegalStateException.class, mailbox::drainAll);
        assertEquals(List.of("+1,1"), holds.tickets, "the failed mail released its own hold count, the survivor still holds");
        assertEquals(1, mailbox.size());

        mailbox.drainAll();
        assertEquals(List.of("survivor"), ran);
        assertEquals(List.of("+1,1", "-1,1"), holds.tickets);
    }
}
