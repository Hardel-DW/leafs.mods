package fr.hardel.leafs;

import fr.hardel.leafs.metrics.TickStages.TickStage;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;

/**
 * The instances of the custom registries of Leafs, the mirror of vanilla's {@code BuiltInRegistries}:
 * the declaration is the creation, the entries register at mod init from their own catalogue class.
 * Unsynced: a client without the mod reads them over the companion mod's wire instead.
 */
public final class LeafsBuiltInRegistries {
    public static final Registry<TickStage> TICK_STAGE = FabricRegistryBuilder.create(LeafsRegistries.TICK_STAGE).buildAndRegister();

    private LeafsBuiltInRegistries() {
    }
}
