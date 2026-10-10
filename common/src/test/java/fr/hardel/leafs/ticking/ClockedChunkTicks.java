package fr.hardel.leafs.ticking;

import net.minecraft.world.ticks.LevelChunkTicks;
import org.jspecify.annotations.Nullable;

public final class ClockedChunkTicks<T> extends LevelChunkTicks<T> implements ClockedTicks {
    private @Nullable RegionTime clock;

    public ClockedChunkTicks(RegionTime clock) {
        this.clock = clock;
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
