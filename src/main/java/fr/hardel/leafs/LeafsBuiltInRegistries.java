package fr.hardel.leafs;

import fr.hardel.leafs.metrics.TickStages.TickStage;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;

/** Leafs' registries, mirror of vanilla's {@code BuiltInRegistries}. Unsynced, the companion mod carries them on its own wire. */
public final class LeafsBuiltInRegistries {
    public static final Registry<TickStage> TICK_STAGE = FabricRegistryBuilder.create(LeafsRegistries.TICK_STAGE).buildAndRegister();

    private LeafsBuiltInRegistries() {
    }
}
