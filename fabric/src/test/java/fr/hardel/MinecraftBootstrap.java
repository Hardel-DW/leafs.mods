package fr.hardel;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** The one bootstrap of vanilla for the tests that touch a registry: loading this class, which JUnit does before the first such class, is the once. Loaded next to the test class so it lands in Fabric's classloader with Minecraft. */
public final class MinecraftBootstrap implements BeforeAllCallback {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MappedRegistry<PoiType> poiTypes = (MappedRegistry<PoiType>) BuiltInRegistries.POINT_OF_INTEREST_TYPE;
        poiTypes.bindAllTagsToEmpty();
        poiTypes.freeze();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
    }
}
