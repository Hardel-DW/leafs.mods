package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.ticking.ClockedTicks;
import fr.hardel.leafs.ticking.RegionTime;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunkTicks.class)
public abstract class LevelChunkTicksMixin implements ClockedTicks {

    @Unique
    private @Nullable RegionTime leafs$clock;

    @Override
    public @Nullable RegionTime leafs$clock() {
        return leafs$clock;
    }

    @Override
    public void leafs$clock(@Nullable RegionTime clock) {
        leafs$clock = clock;
    }
}
