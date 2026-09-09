package fr.hardel;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** The one bootstrap of vanilla for the tests that touch a registry: loading this class, which JUnit does before the first such class, is the once. */
public final class MinecraftBootstrap implements BeforeAllCallback {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
    }
}
