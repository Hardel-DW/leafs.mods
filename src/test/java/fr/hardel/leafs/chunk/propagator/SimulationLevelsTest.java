package fr.hardel.leafs.chunk.propagator;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Publishes per chunk the level vanilla's SimulationChunkTracker computed, 33 meaning not simulated. */
class SimulationLevelsTest {

    private final SimulationLevels levels = new SimulationLevels();

    /** Reads MAX_LEVEL after the bootstrap: the chunk pyramid behind it needs the registries. */
    private static int noTicket() {
        return ChunkLevel.MAX_LEVEL + 1;
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A player simulation ticket at simulation distance 10 carries level 21; each chunk of distance adds one. */
    @Test
    void simulationTicketSimulatesItsRadius() {
        levels.feed(ChunkPos.pack(0, 0), 21);
        levels.drain();

        assertEquals(21, levels.level(ChunkPos.pack(0, 0)));
        assertEquals(31, levels.level(ChunkPos.pack(10, 0)));
        assertTrue(ChunkLevel.isEntityTicking(levels.level(ChunkPos.pack(-10, 10))));
        assertFalse(ChunkLevel.isEntityTicking(levels.level(ChunkPos.pack(11, 0))));
        assertTrue(ChunkLevel.isBlockTicking(levels.level(ChunkPos.pack(11, 11))));
        assertEquals(33, levels.level(ChunkPos.pack(12, 0)));
        assertEquals(33, levels.level(ChunkPos.pack(100, 100)));
    }

    @Test
    void removalClearsTheRadius() {
        levels.feed(ChunkPos.pack(0, 0), 21);
        levels.drain();
        levels.feed(ChunkPos.pack(0, 0), noTicket());
        levels.drain();

        assertEquals(33, levels.level(ChunkPos.pack(0, 0)));
        assertEquals(33, levels.level(ChunkPos.pack(5, -3)));
    }

    /** Two tickets overlap: each chunk keeps the strongest reach, and losing one falls back to the other. */
    @Test
    void overlappingTicketsKeepTheStrongest() {
        levels.feed(ChunkPos.pack(0, 0), 21);
        levels.feed(ChunkPos.pack(6, 0), 29);
        levels.drain();
        assertEquals(24, levels.level(ChunkPos.pack(3, 0)));

        levels.feed(ChunkPos.pack(0, 0), noTicket());
        levels.drain();
        assertEquals(32, levels.level(ChunkPos.pack(3, 0)));
    }

    /** A ticket that simulates nothing publishes nothing, level 33 is where block ticking already stops. */
    @Test
    void nonSimulatingLevelPublishesNothing() {
        levels.feed(ChunkPos.pack(0, 0), 33);
        levels.drain();

        assertEquals(33, levels.level(ChunkPos.pack(0, 0)));
    }

    /** Two threads feed far apart tickets and drain concurrently, the published levels match both radii. */
    @Test
    void concurrentFeedsPublishBothRadii() throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        Runnable near = () -> {
            awaitStart(start);
            levels.feed(ChunkPos.pack(0, 0), 21);
            levels.drain();
        };
        Runnable far = () -> {
            awaitStart(start);
            levels.feed(ChunkPos.pack(1000, 1000), 25);
            levels.drain();
        };

        Thread first = Thread.ofPlatform().start(near);
        Thread second = Thread.ofPlatform().start(far);
        start.countDown();
        first.join();
        second.join();

        assertEquals(26, levels.level(ChunkPos.pack(5, 0)));
        assertEquals(30, levels.level(ChunkPos.pack(1005, 1000)));
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
