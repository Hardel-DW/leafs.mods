package fr.hardel.leafs.metrics;

/**
 * The stages of one level-serial tick, in the execution order of {@code LevelTickUnit} and of the
 * shrunk vanilla level tick it runs. A stage whose anchor never executes leaves its slot at zero and
 * its time joins the next marked stage, so the crumbs of routed-away vanilla phases land in the
 * neighbouring bucket instead of vanishing.
 */
public enum SerialStage implements TickStage {
    TASKS,
    SCHEDULERS,
    BORDER,
    WEATHER,
    TIME,
    RAIDS,
    PURGE,
    VIEW,
    TRACKING,
    UNLOADS,
    DRAGON,
    MANAGEMENT
}
