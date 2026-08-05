package fr.hardel.leafs.ticking;

import net.minecraft.server.level.ServerLevel;

/** Work other modules contribute around each level tick - entity/ drains its schedulers before. */
public interface LevelTickPhases {

    void beforeLevelTick(ServerLevel level);

    void afterLevelTick(ServerLevel level);
}
