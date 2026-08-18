package fr.hardel.leafs;

import fr.hardel.leafs.metrics.TickStages.TickStage;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** The keys of the custom registries of Leafs, the mirror of vanilla's {@code Registries}; the instances live in {@link LeafsBuiltInRegistries}. */
public final class LeafsRegistries {
    public static final ResourceKey<Registry<TickStage>> TICK_STAGE = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "tick_stage"));

    private LeafsRegistries() {
    }
}
