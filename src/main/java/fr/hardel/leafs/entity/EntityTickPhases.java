package fr.hardel.leafs.entity;

import fr.hardel.leafs.ticking.LevelTickPhases;
import net.minecraft.server.level.ServerLevel;

/** Region-side entity-scheduler ticking: drained before the level tick, after inbound packets. */
public final class EntityTickPhases implements LevelTickPhases {

    @Override
    public void beforeLevelTick(ServerLevel level) {
        ((ServerEntityAccess) level.getServer()).leafs$entitySchedulers().tickLevel(level);
    }

    @Override
    public void afterLevelTick(ServerLevel level) {
    }
}
