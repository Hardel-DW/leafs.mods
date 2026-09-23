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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
