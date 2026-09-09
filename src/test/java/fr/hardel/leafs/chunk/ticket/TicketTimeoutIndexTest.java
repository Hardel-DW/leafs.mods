package fr.hardel.leafs.chunk.ticket;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanilla's purge and a refresh never overlap on the server thread: here a section counts down under the storage monitor, so a refresh waits for it. */
@ExtendWith(MinecraftBootstrap.class)
class TicketTimeoutIndexTest {
    private static final long CHUNK = ChunkPos.pack(3, 3);
    private static final long SECTION = CoordinateKey.pack(1, 1);

    @Test
    void aRefreshWaitsForTheCountdownOfItsSection() throws InterruptedException {
        TicketStorage storage = new TicketStorage();
        TicketStorageAccess access = (TicketStorageAccess) storage;
        TicketTimeoutIndex timeouts = new TicketTimeoutIndex(storage, access.leafs$graphs(), 1);
        access.leafs$bindTimeouts(timeouts);
        storage.addTicket(CHUNK, new Ticket(TicketType.PORTAL, 33));
        CountDownLatch countingDown = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        timeouts.pauseWhile(_ -> {
            countingDown.countDown();
            await(release);
            return false;
        });

        Thread purger = new Thread(() -> timeouts.purgeSections(new long[]{SECTION}));
        purger.start();
        countingDown.await();
        Thread refresher = new Thread(() -> storage.addTicket(CHUNK, new Ticket(TicketType.PORTAL, 33)));
        refresher.start();

        assertBlocked(refresher);
        release.countDown();
        purger.join();
        refresher.join();
        assertEquals(1, storage.getTickets(CHUNK).size());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED) {
            assertTrue(thread.isAlive(), "the refresh ran during the countdown");
            assertTrue(System.nanoTime() < deadline, "the refresh never reached the storage monitor");
            Thread.onSpinWait();
        }
    }
}
