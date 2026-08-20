package fr.hardel.leafs.chunk;

import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The direct replacement of vanilla's naturalSpawnChunkCounter: a player marks the chunks within
 * Chebyshev distance 8, the inscribed square within 5 answers TRUE without the exact euclidean test.
 */
class SpawnProximityTest {

    private final SpawnProximity proximity = new SpawnProximity();

    @Test
    void onePlayerCoversItsDisk() {
        proximity.add(0, 0);

        assertTrue(proximity.covered(ChunkPos.pack(8, 8)));
        assertFalse(proximity.covered(ChunkPos.pack(9, 0)));
        assertEquals(TriState.TRUE, proximity.nearby(ChunkPos.pack(5, -5)));
        assertEquals(TriState.DEFAULT, proximity.nearby(ChunkPos.pack(6, 0)));
        assertEquals(TriState.DEFAULT, proximity.nearby(ChunkPos.pack(8, 8)));
        assertEquals(TriState.FALSE, proximity.nearby(ChunkPos.pack(9, 9)));
    }

    /** Two overlapping players refcount: the chunk stays covered until the last one leaves. */
    @Test
    void overlappingPlayersRefcount() {
        proximity.add(0, 0);
        proximity.add(4, 0);
        proximity.remove(0, 0);

        assertFalse(proximity.covered(ChunkPos.pack(-8, 0)));
        assertTrue(proximity.covered(ChunkPos.pack(12, 0)));
        assertEquals(TriState.TRUE, proximity.nearby(ChunkPos.pack(4, 0)));
    }

    @Test
    void leavingClearsEverything() {
        proximity.add(3, 3);
        proximity.remove(3, 3);

        assertFalse(proximity.covered(ChunkPos.pack(3, 3)));
        assertEquals(TriState.FALSE, proximity.nearby(ChunkPos.pack(3, 3)));
    }
}
