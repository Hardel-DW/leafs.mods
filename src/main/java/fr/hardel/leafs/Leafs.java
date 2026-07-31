package fr.hardel.leafs;

import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.network.RegionNetworkPhases;
import fr.hardel.leafs.ownership.Ownership;
import fr.hardel.leafs.ticking.TickingManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Leafs implements ModInitializer {
    public static final String MOD_ID = "leafs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LeafsConfig.load();
        TickingManager.installPhases(new RegionNetworkPhases());

        LeafsConfig config = LeafsConfig.get();
        LOGGER.info("Leafs initialised — {} region threads, {}x{}-chunk sections, ownership checks {}",
            config.effectiveRegionThreads(), config.sectionChunkSize(), config.sectionChunkSize(),
            Ownership.CHECKS_ENABLED ? "on" : "off");
    }
}
