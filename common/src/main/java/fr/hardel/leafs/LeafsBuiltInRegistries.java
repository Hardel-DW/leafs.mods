package fr.hardel.leafs;

import fr.hardel.leafs.metrics.TickStages.TickStage;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

public final class LeafsBuiltInRegistries {
    public static final Registry<TickStage> TICK_STAGE = new MappedRegistry<>(LeafsRegistries.TICK_STAGE, Lifecycle.stable());

    private LeafsBuiltInRegistries() {
    }
}
