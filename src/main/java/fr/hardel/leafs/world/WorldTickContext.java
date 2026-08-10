package fr.hardel.leafs.world;

import fr.hardel.leafs.entity.RegionEntityData;

/** The tick unit's payload whose tick body runs on this thread, scoped to its level by identity. */
public final class WorldTickContext {
    private static final ThreadLocal<WorldTickContext> CURRENT = new ThreadLocal<>();

    private final Object scope;
    private final RegionWorldData worldData;
    private final RegionEntityData entityData;

    private WorldTickContext(Object scope, RegionWorldData worldData, RegionEntityData entityData) {
        this.scope = scope;
        this.worldData = worldData;
        this.entityData = entityData;
    }

    public static void enter(Object scope, RegionWorldData worldData, RegionEntityData entityData) {
        CURRENT.set(new WorldTickContext(scope, worldData, entityData));
    }

    public static void exit() {
        CURRENT.remove();
    }

    public static RegionWorldData activeFor(Object scope) {
        WorldTickContext context = CURRENT.get();

        return context != null && context.scope == scope ? context.worldData : null;
    }

    /** Identity is scope enough here: an entity payload belongs to exactly one unit of one level. */
    public static boolean ownsEntityData(RegionEntityData entityData) {
        WorldTickContext context = CURRENT.get();

        return context != null && context.entityData == entityData;
    }
}
