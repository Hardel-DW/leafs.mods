package fr.hardel.leafs.network;

import fr.hardel.leafs.ticking.LevelTickPhases;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionNetworkPhasesTest {

    /**
     * The 2026-08-04 paused-GUI bug: vanilla drains packets every loop iteration even while the
     * integrated server is paused ({@code processPacketsAndTick}), so the stolen per-player queues
     * must too - a pause-screen (command block edit) froze every queued interaction until unpause.
     * Gameplay phases stay paused with the world, matching vanilla.
     */
    @Test
    void onlyNetworkPhasesRunWhilePaused() {
        assertTrue(new RegionNetworkPhases().runsWhilePaused());
        assertFalse(new LevelTickPhases() {
            @Override
            public void beforeLevelTick(ServerLevel level) {
            }

            @Override
            public void afterLevelTick(ServerLevel level) {
            }
        }.runsWhilePaused());
    }
}
