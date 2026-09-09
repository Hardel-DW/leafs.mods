package fr.hardel.leafs.chunk.level;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The players graph as the bench drives it: five bots on a ring, one source each, moving a chunk at a time from five threads. */
class PlayersGraphTest {
    private static final int LEVELS = 34;
    private static final int PLAYERS = 5;
    private static final int RING_CHUNKS = 125;
    private static final int STEPS = 300;

    private final ChunkLevels graph = new ChunkLevels(LEVELS);

    @Test
    void fiveMovingBotsSettleInMillisecondsFromFiveThreads() throws InterruptedException {
        int[][] positions = new int[PLAYERS][2];
        for (int player = 0; player < PLAYERS; player++) {
            double angle = 2 * Math.PI * player / PLAYERS;
            positions[player][0] = (int) Math.round(Math.cos(angle) * RING_CHUNKS);
            positions[player][1] = (int) Math.round(Math.sin(angle) * RING_CHUNKS);
            graph.setSource(positions[player][0], positions[player][1], 0);
        }

        graph.drain((key, old, now) -> {});
        CountDownLatch done = new CountDownLatch(PLAYERS);
        List<Throwable> failures = new ArrayList<>();
        long start = System.nanoTime();
        for (int player = 0; player < PLAYERS; player++) {
            int[] position = positions[player];
            Thread bot = new Thread(() -> {
                try {
                    for (int step = 0; step < STEPS; step++) {
                        graph.setSource(position[0], position[1], graph.none());
                        position[0] += 1;
                        position[1] += step % 2;
                        graph.setSource(position[0], position[1], 0);
                        graph.drain((key, old, now) -> {});
                    }
                } catch (Throwable failure) {
                    synchronized (failures) {
                        failures.add(failure);
                    }
                } finally {
                    done.countDown();
                }
            });
            bot.start();
        }

        assertTrue(done.await(20, TimeUnit.SECONDS), "the bots never finished");
        assertEquals(List.of(), failures);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(millis < 5_000, "the moving bots took " + millis + " ms");
        for (int[] position : positions) {
            assertEquals(0, graph.level(ChunkPos.pack(position[0], position[1])));
            assertEquals(32, graph.level(ChunkPos.pack(position[0] + 32, position[1])));
            assertEquals(graph.none(), graph.level(ChunkPos.pack(position[0] - 40, position[1])));
        }
    }
}
