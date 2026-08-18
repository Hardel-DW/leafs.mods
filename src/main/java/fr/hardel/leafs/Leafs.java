package fr.hardel.leafs;

import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.debug.LeafsCommand;
import fr.hardel.leafs.global.LeafsGameRules;
import fr.hardel.leafs.metrics.TickStages;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Leafs implements ModInitializer {
    public static final String MOD_ID = "leafs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LeafsConfig.register();
        LeafsTicketTypes.register();
        TickStages.register();
        LeafsGameRules.register();
        LeafsCommand.register();

        LeafsConfig config = LeafsConfig.get();
        LOGGER.info("Leafs initialised - {} region workers, {}x{}-chunk sections", config.effectiveThreads(), config.sectionSize(), config.sectionSize());
    }
}
