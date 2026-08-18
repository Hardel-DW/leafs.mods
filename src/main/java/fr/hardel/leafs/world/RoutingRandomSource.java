package fr.hardel.leafs.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.NonNull;

/** Swapped into the level's random field: resolves per call to the ticking unit's random, vanilla otherwise. */
public final class RoutingRandomSource implements RandomSource {
    private final Object scope;
    private final RandomSource vanilla;

    public RoutingRandomSource(Object scope, RandomSource vanilla) {
        this.scope = scope;
        this.vanilla = vanilla;
    }

    public static RandomSource unwrap(RandomSource random) {
        return random instanceof RoutingRandomSource routing ? routing.vanilla : random;
    }

    private RandomSource resolve() {
        RegionWorldData data = WorldTickContext.activeFor(scope);
        return data == null ? vanilla : data.random();
    }

    @Override
    public @NonNull RandomSource fork() {
        return resolve().fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return resolve().forkPositional();
    }

    @Override
    public void setSeed(long seed) {
        resolve().setSeed(seed);
    }

    @Override
    public int nextInt() {
        return resolve().nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return resolve().nextInt(bound);
    }

    @Override
    public long nextLong() {
        return resolve().nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return resolve().nextBoolean();
    }

    @Override
    public float nextFloat() {
        return resolve().nextFloat();
    }

    @Override
    public double nextDouble() {
        return resolve().nextDouble();
    }

    @Override
    public double nextGaussian() {
        return resolve().nextGaussian();
    }
}
