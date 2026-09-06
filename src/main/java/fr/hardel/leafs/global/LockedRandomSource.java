package fr.hardel.leafs.global;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.NonNull;

/** A shared sequence RandomSource serialized on the monitor the save encode takes: a roll and a save exclude each other. */
public final class LockedRandomSource implements RandomSource {
    private final RandomSource delegate;
    private final Object monitor;

    public LockedRandomSource(RandomSource delegate, Object monitor) {
        this.delegate = delegate;
        this.monitor = monitor;
    }

    @Override
    public @NonNull RandomSource fork() {
        synchronized (monitor) {
            return delegate.fork();
        }
    }

    @Override
    public @NonNull PositionalRandomFactory forkPositional() {
        synchronized (monitor) {
            return delegate.forkPositional();
        }
    }

    @Override
    public void setSeed(long seed) {
        synchronized (monitor) {
            delegate.setSeed(seed);
        }
    }

    @Override
    public int nextInt() {
        synchronized (monitor) {
            return delegate.nextInt();
        }
    }

    @Override
    public int nextInt(int bound) {
        synchronized (monitor) {
            return delegate.nextInt(bound);
        }
    }

    @Override
    public long nextLong() {
        synchronized (monitor) {
            return delegate.nextLong();
        }
    }

    @Override
    public boolean nextBoolean() {
        synchronized (monitor) {
            return delegate.nextBoolean();
        }
    }

    @Override
    public float nextFloat() {
        synchronized (monitor) {
            return delegate.nextFloat();
        }
    }

    @Override
    public double nextDouble() {
        synchronized (monitor) {
            return delegate.nextDouble();
        }
    }

    @Override
    public double nextGaussian() {
        synchronized (monitor) {
            return delegate.nextGaussian();
        }
    }
}
