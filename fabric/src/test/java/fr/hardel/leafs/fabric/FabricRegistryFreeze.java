package fr.hardel.leafs.fabric;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Fabric defers vanilla's registry freeze to mod initialisation, which no test runs: the point of interest registry gets its tags and its freeze here, once, after the bootstrap. */
public final class FabricRegistryFreeze implements BeforeAllCallback {

    static {
        MappedRegistry<PoiType> poiTypes = (MappedRegistry<PoiType>) BuiltInRegistries.POINT_OF_INTEREST_TYPE;
        poiTypes.bindAllTagsToEmpty();
        poiTypes.freeze();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
    }
}
