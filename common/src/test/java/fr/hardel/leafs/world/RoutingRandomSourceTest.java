package fr.hardel.leafs.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingRandomSourceTest {
    private final RandomSource unitRandom = fixed(2);
    private final RoutingRandomSource routing = new RoutingRandomSource(null);
    private final RegionWorldData worldData = new RegionWorldData(() -> 0L, unitRandom, null, new PathTypeCache());

    @AfterEach
    void exitContext() {
        WorldTickContext.exit();
    }

    @Test
    void contextForTheLevelResolvesTheRegionRandom() {
        routing.setSeed(7);
        WorldTickContext.enter(null, null, worldData);

        assertEquals(2, routing.nextInt());
        WorldTickContext.exit();
        assertEquals(RandomSource.create(7).nextInt(), routing.nextInt(), "back to this thread's own random, untouched by the region");
    }

    /** ATM11, 14 September 2026: a chunk worker promoting a chunk and the server thread shared the level's random, which refuses two threads. */
    @Test
    void offARegionEachThreadHasItsOwnRandom() throws InterruptedException {
        routing.setSeed(1);
        routing.nextInt();
        Thread other = new Thread(() -> {
            routing.setSeed(2);
            routing.nextInt();
        });
        other.start();
        other.join();

        RandomSource reference = RandomSource.create(1);
        reference.nextInt();
        assertEquals(reference.nextInt(), routing.nextInt(), "the other thread seeded its own random, not this thread's");
    }

    private static RandomSource fixed(int value) {
        return new RandomSource() {
            @Override
            public RandomSource fork() {
                return this;
            }

            @Override
            public PositionalRandomFactory forkPositional() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setSeed(long seed) {
            }

            @Override
            public int nextInt() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }

            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public boolean nextBoolean() {
                return false;
            }

            @Override
            public float nextFloat() {
                return value;
            }

            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public double nextGaussian() {
                return value;
            }
        };
    }
}
