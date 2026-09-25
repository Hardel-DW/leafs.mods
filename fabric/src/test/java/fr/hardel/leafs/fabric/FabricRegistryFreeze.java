package fr.hardel.leafs.fabric;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

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
