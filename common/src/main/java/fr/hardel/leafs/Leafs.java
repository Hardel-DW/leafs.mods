package fr.hardel.leafs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Leafs {
    public static final String MOD_ID = "leafs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ThreadGroup serverThreads;

    private Leafs() {
    }

    public static void bootstrap(Path configDir, Path gameDir, ThreadGroup serverThreads) {
        Leafs.serverThreads = serverThreads;
        LeafsConfig.register(configDir, gameDir);
        LeafsConfig config = LeafsConfig.get();
        LOGGER.info("Leafs initialised - {} region workers, {} chunk workers, {}x{}-chunk sections", config.effectiveRegionThreads(), config.effectiveChunkThreads(), config.sectionSize(), config.sectionSize());
    }

    public static ThreadGroup serverThreads() {
        return serverThreads;
    }
}
