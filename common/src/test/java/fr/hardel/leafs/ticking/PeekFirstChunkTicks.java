package fr.hardel.leafs.ticking;

import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.Nullable;

/** Polls only after a peek found a tick, as vanilla's LevelTicks does: Lithium replaces poll() and relies on it. */
public final class PeekFirstChunkTicks<T> extends LevelChunkTicks<T> implements ClockedTicks {
    private @Nullable RegionTime clock;

    public PeekFirstChunkTicks(RegionTime clock) {
        this.clock = clock;
    }

    @Override
    public @Nullable ScheduledTick<T> poll() {
        if (peek() == null) {
            throw new IllegalStateException("poll() without a tick to poll, Lithium throws a NullPointerException here");
        }

        return super.poll();
    }

    @Override
    public @Nullable RegionTime leafs$clock() {
        return clock;
    }

    @Override
    public void leafs$clock(@Nullable RegionTime clock) {
        this.clock = clock;
    }
}
