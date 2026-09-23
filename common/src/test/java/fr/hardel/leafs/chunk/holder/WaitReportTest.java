package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.chunk.ChunkFixtures;
import fr.hardel.leafs.chunk.owner.ChunkClaim;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.global.GlobalScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaitReportTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final ChunkOwners owners = ChunkFixtures.owners(pool, (_, _) -> null, (_, _) -> false, (_, _, _) -> false, new GlobalScheduler(Runnable::run));

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void aTakenChunkReportsItsTakerAndItsMail() throws InterruptedException {
        assertNull(WaitReport.taken(owners, 1, 1));

        AtomicReference<ChunkClaim> claim = new AtomicReference<>();
        Thread taker = new Thread(() -> claim.set(owners.borrow(1, 1)), "taker");
        taker.start();
        taker.join();
        owners.submit(1, 1, Work.GAME, () -> { });

        assertEquals("taken by thread 'taker' with 1 queued", WaitReport.taken(owners, 1, 1));
        owners.release(1, 1, claim.get());
        assertNull(WaitReport.taken(owners, 1, 1));
    }
}
