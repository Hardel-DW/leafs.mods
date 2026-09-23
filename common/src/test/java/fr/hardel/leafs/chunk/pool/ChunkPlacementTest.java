package fr.hardel.leafs.chunk.pool;

import fr.hardel.leafs.chunk.ChunkFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ChunkPlacementTest {
    private final ChunkPool pool = ChunkFixtures.pool(1);
    private final ChunkPlacement placement = new ChunkPlacement(pool, 0, (_, _) -> 0);

    @AfterEach
    void stop() {
        pool.shutdown();
    }

    @Test
    void lightReservesInItsOwnSpace() {
        assertEquals(placement.area(ChunkTask.Kind.STEP, 1, 1, 0)[0], placement.area(ChunkTask.Kind.OWNER, 1, 1, 0)[0], "publication and generation write the blocks");
        assertNotEquals(placement.area(ChunkTask.Kind.STEP, 1, 1, 0)[0], placement.area(ChunkTask.Kind.LIGHT, 1, 1, 0)[0], "light writes the light arrays");
    }
}
