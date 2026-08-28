package fr.hardel.leafs.chunk;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkMailboxTest {

    /** Records the hold tickets as "+x,z" and "-x,z" in order, FULL holds with a suffix. */
    private static final class RecordingHolds implements ChunkHoldController {
        final List<String> tickets = new ArrayList<>();

        @Override
        public void addHold(int chunkX, int chunkZ, MailHold.Level level) {
            tickets.add("+" + chunkX + "," + chunkZ + suffix(level));
        }

        @Override
        public void removeHold(int chunkX, int chunkZ, MailHold.Level level) {
            tickets.add("-" + chunkX + "," + chunkZ + suffix(level));
        }

        private static String suffix(MailHold.Level level) {
            return level == MailHold.Level.FULL ? " full" : "";
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

    @Test
    void aTickingPromotionHoldsItsNeighbourhoodAtFullUntilItRan() {
        RecordingHolds holds = new RecordingHolds();
        ChunkMailbox mailbox = new ChunkMailbox(holds);
        mailbox.post(0, 0, MailHold.FULL_NEIGHBOURHOOD, () -> {
        });
        mailbox.post(1, 0, MailHold.FULL_NEIGHBOURHOOD, () -> {
        });

        List<String> added = holds.tickets.stream().filter(ticket -> ticket.startsWith("+")).toList();
        assertEquals(12, added.size(), "two overlapping 3x3, each chunk held once");
        assertEquals(12, Set.copyOf(added).size());
        assertEquals(List.of("+-1,-1 full", "+-1,0 full", "+-1,1 full"), added.subList(0, 3));

        assertEquals(1, mailbox.drainOrphans(key -> key == ChunkPos.pack(0, 0)));
        assertEquals(3, holds.tickets.stream().filter(ticket -> ticket.startsWith("-")).count(), "only the column the second promotion does not share is released");

        assertEquals(1, mailbox.drainOrphans(key -> key == ChunkPos.pack(1, 0)));
        assertEquals(12, holds.tickets.stream().filter(ticket -> ticket.startsWith("-")).count());
    }
}
