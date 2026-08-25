package fr.hardel.leafs.world;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RoutingRandomSourceTest {
    private final Object scope = new Object();
    private final RandomSource vanilla = fixed(1);
    private final RandomSource unitRandom = fixed(2);
    private final RoutingRandomSource routing = new RoutingRandomSource(scope, vanilla);
    private final RegionWorldData worldData = new RegionWorldData(() -> 0L, () -> 0L, _ -> true, new ObjectLinkedOpenHashSet<>(), unitRandom, null, new HashSet<>(), new PathTypeCache());

    @AfterEach
    void exitContext() {
        WorldTickContext.exit();
    }

    @Test
    void contextForAnotherScopeResolvesVanilla() {
        WorldTickContext.enter(new Object(), worldData, null);

        assertEquals(1, routing.nextInt());
    }

    @Test
    void contextForTheScopeResolvesTheUnitRandom() {
        WorldTickContext.enter(scope, worldData, null);

        assertEquals(2, routing.nextInt());
        WorldTickContext.exit();
        assertEquals(1, routing.nextInt());
    }

    @Test
    void unwrapReturnsTheVanillaInstance() {
        assertSame(vanilla, RoutingRandomSource.unwrap(routing));
        assertSame(vanilla, RoutingRandomSource.unwrap(vanilla));
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
