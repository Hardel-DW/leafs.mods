package fr.hardel.leafs.chunk.ticket;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.TicketStorageAccess;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class TicketStorageTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    /** 2026-09-24: the server thread settled the tickets other threads added, and waited on the graph locks of their chunks. */
    @Test
    void aTicketSettlesOnTheThreadThatAddsIt() throws InterruptedException {
        TicketStorage storage = new TicketStorage();
        storage.setLoadingChunkUpdatedListener(null);
        List<String> settlers = new CopyOnWriteArrayList<>();
        ((TicketStorageAccess) storage).leafs$graphs().listen(() -> (_, _, _) -> settlers.add(Thread.currentThread().getName()), (_, _, _) -> { }, (_, _, _) -> { }, pool);
        CountDownLatch release = TestThreads.occupy(pool);

        Thread writer = new Thread(() -> storage.addTicket(ChunkPos.pack(0, 0), new Ticket(TicketType.FORCED, 31)), "writer");
        writer.start();
        writer.join();
        assertEquals(List.of("writer"), settlers.stream().distinct().toList());

        release.countDown();
        CountDownLatch drained = new CountDownLatch(1);
        pool.execute(drained::countDown);
        TestThreads.await(drained);
        assertEquals(List.of("writer"), settlers.stream().distinct().toList());
    }

    /** 2026-09-25: a region adding a light ticket in its own loaded chunk waited on the stripes a pool drain held. */
    @Test
    void aTicketThatLowersNoLevelReturnsWhileADrainHoldsTheStripes() throws InterruptedException {
        TicketStorage storage = new TicketStorage();
        storage.setLoadingChunkUpdatedListener(null);
        TicketGraphs graphs = ((TicketStorageAccess) storage).leafs$graphs();
        graphs.listen(() -> (_, _, _) -> { }, (_, _, _) -> { }, (_, _, _) -> { }, pool);
        long key = ChunkPos.pack(0, 0);
        storage.addTicket(key, new Ticket(TicketType.FORCED, 31));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        new Thread(() -> graphs.loading().settled(0, 0, (_, _, _) -> { }, () -> {
            held.countDown();
            TestThreads.await(release);
            return null;
        })).start();
        TestThreads.await(held);

        CountDownLatch added = new CountDownLatch(1);
        new Thread(() -> {
            storage.addTicket(key, new Ticket(TicketType.FORCED, 33));
            added.countDown();
        }).start();
        boolean returned = added.await(1, TimeUnit.SECONDS);
        release.countDown();
        assertTrue(returned);
    }
}
