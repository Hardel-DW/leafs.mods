package fr.hardel.leafs;

import fr.hardel.leafs.metrics.ModAttribution;
import fr.hardel.leafs.metrics.TickStages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** What the mod does, whichever loader starts it; the loader module hands over what only it knows. */
public final class Leafs {
    public static final String MOD_ID = "leafs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static Platform platform;

    /** The loader's side of the world: where its files are, and which mod owns a class. */
    public record Platform(Path configDir, Path gameDir, ModAttribution attribution) {
    }

    private Leafs() {
    }

    public static void bootstrap(Platform platform) {
        Leafs.platform = platform;
        LeafsConfig.register(platform.configDir(), platform.gameDir());
        TickStages.register();

        LeafsConfig config = LeafsConfig.get();
        LOGGER.info("Leafs initialised - {} region workers, {} chunk workers, {}x{}-chunk sections", config.effectiveRegionThreads(), config.effectiveChunkThreads(), config.sectionSize(), config.sectionSize());
    }

    public static Platform platform() {
        return platform;
    }
}
