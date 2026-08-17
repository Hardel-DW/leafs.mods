package fr.hardel.leafs.metrics;

/** The stages of one region tick, in the execution order of {@code RegionTickBody}. */
public enum RegionStage implements TickStage {
    TASKS,
    UNLOADS,
    PACKETS,
    BLOCK_TICKS,
    FLUID_TICKS,
    SPAWN_CENSUS,
    CHUNK_TICK,
    BROADCAST,
    TRACKING,
    BLOCK_EVENTS,
    ENTITIES,
    BLOCK_ENTITIES,
    PLAYERS
}
