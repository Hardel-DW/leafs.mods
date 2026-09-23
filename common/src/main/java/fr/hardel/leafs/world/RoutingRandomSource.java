package fr.hardel.leafs.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.NonNull;

public final class RoutingRandomSource implements RandomSource {
    private static final ThreadLocal<RandomSource> OWN = ThreadLocal.withInitial(RandomSource::create);

    private final ServerLevel level;

    public RoutingRandomSource(ServerLevel level) {
        this.level = level;
    }

    private RandomSource resolve() {
        RegionWorldData data = WorldTickContext.activeFor(level);
        return data == null ? OWN.get() : data.random();
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
