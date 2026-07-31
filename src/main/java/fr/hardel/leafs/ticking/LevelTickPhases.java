package fr.hardel.leafs.ticking;

import net.minecraft.server.level.ServerLevel;

/** Work other modules contribute around each level tick — network/ drains inbound before, ticks connections after. */
public interface LevelTickPhases {

    LevelTickPhases NONE = new LevelTickPhases() {
        @Override
        public void beforeLevelTick(ServerLevel level) {
        }

        @Override
        public void afterLevelTick(ServerLevel level) {
        }
    };

    void beforeLevelTick(ServerLevel level);

    void afterLevelTick(ServerLevel level);
}
