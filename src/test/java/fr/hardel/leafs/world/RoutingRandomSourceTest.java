package fr.hardel.leafs.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingRandomSourceTest {
    private final RandomSource vanilla = fixed(1);
    private final RandomSource unitRandom = fixed(2);
    private final RoutingRandomSource routing = new RoutingRandomSource(null, vanilla);
    private final RegionWorldData worldData = new RegionWorldData(() -> 0L, unitRandom, null, new PathTypeCache(), 0L);

    @AfterEach
    void exitContext() {
        WorldTickContext.exit();
    }

    @Test
    void contextForTheLevelResolvesTheRegionRandom() {
        WorldTickContext.enter(null, null, worldData);

        assertEquals(2, routing.nextInt());
        WorldTickContext.exit();
        assertEquals(1, routing.nextInt());
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
