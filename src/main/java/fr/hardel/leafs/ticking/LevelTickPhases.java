package fr.hardel.leafs.ticking;

import net.minecraft.server.level.ServerLevel;

/** Work other modules contribute around each level tick - network/ drains inbound before, ticks connections after. */
public interface LevelTickPhases {

    void beforeLevelTick(ServerLevel level);

    void afterLevelTick(ServerLevel level);

    /** Vanilla drains packets every loop iteration even paused; network phases must too, gameplay phases freeze with the world. */
    default boolean runsWhilePaused() {
        return false;
    }
}
