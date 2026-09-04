package fr.hardel.leafs.chunk.level;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Five players flying: each step a view square of tickets slides one chunk, and the graph must settle in milliseconds. */
class MovingViewTest {
    private static final int LEVELS = 46;
    private static final int VIEW = 10;
    private static final int PLAYERS = 5;
    private static final int STEPS = 200;

    private final ChunkLevels graph = new ChunkLevels(LEVELS);

    @Test
    void aSlidingViewSettlesInMilliseconds() {
        int[] centers = new int[PLAYERS];
        for (int player = 0; player < PLAYERS; player++) {
            centers[player] = player * 400;
            square(centers[player], 0, VIEW, 31);
        }

        graph.drain((key, old, now) -> {});
        long start = System.nanoTime();
        for (int step = 0; step < STEPS; step++) {
            for (int player = 0; player < PLAYERS; player++) {
                int center = centers[player];
                for (int dz = -VIEW; dz <= VIEW; dz++) {
                    graph.setSource(center - VIEW, dz, graph.none());
                    graph.setSource(center + VIEW + 1, dz, 31);
                }

                centers[player] = center + 1;
            }

            graph.drain((key, old, now) -> {});
        }

        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(millis < 2_000, "the sliding views took " + millis + " ms");
        assertEquals(31, graph.level(ChunkPos.pack(STEPS, 0)));
        assertEquals(31 + VIEW + 1, graph.level(ChunkPos.pack(STEPS + 2 * VIEW + 1, 0)));
        assertEquals(graph.none(), graph.level(ChunkPos.pack(-VIEW - 14, 0)));
    }

    private void square(int centerX, int centerZ, int radius, int level) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                graph.setSource(centerX + dx, centerZ + dz, level);
            }
        }
    }
}
