package fr.hardel.leafs.chunk.view;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.pool.ChunkPlacement;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MinecraftBootstrap.class)
class PlayerViewTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final TicketGraphs graphs = new TicketGraphs();
    private final PlayerView view = new PlayerView(new TicketStorage(), graphs);
    private final ChunkPlacement placement = new ChunkPlacement(pool, 0, view::urgency);

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    /** 2026-09-24: a far chunk waited behind the pregen steps to unload, and stayed in memory meanwhile. */
    @Test
    void aFarUnloadPassesBeforeTheQueuedPregenSteps() {
        view.viewDistance(10);
        graphs.loading().setSource(0, 0, ChunkLevel.byStatus(ChunkStatus.FULL));
        graphs.loading().drain((_, _, _) -> { });
        List<String> ran = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(2);
        CountDownLatch release = TestThreads.occupy(pool);

        placement.onPool(ChunkTask.Kind.STEP, 0, 0, 0, () -> {
            ran.add("pregen step");
            done.countDown();
        });
        placement.onPool(ChunkTask.Kind.OWNER, 50, 50, 0, () -> {
            ran.add("unload");
            done.countDown();
        });
        release.countDown();
        TestThreads.await(done);

        assertEquals(List.of("unload", "pregen step"), ran);
    }
}
