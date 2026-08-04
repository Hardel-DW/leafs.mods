package fr.hardel.leafs.world;

/** The world data whose tick body runs on this thread, scoped to its level by identity. */
public final class WorldTickContext {
    private static final ThreadLocal<WorldTickContext> CURRENT = new ThreadLocal<>();

    private final Object scope;
    private final RegionWorldData worldData;

    private WorldTickContext(Object scope, RegionWorldData worldData) {
        this.scope = scope;
        this.worldData = worldData;
    }

    public static void enter(Object scope, RegionWorldData worldData) {
        CURRENT.set(new WorldTickContext(scope, worldData));
    }

    public static void exit() {
        CURRENT.remove();
    }

    public static RegionWorldData activeFor(Object scope) {
        WorldTickContext context = CURRENT.get();

        return context != null && context.scope == scope ? context.worldData : null;
    }
}
