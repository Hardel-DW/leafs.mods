package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.RegionTickData;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public final class WorldTickContext {
    private static final ThreadLocal<WorldTickContext> CURRENT = new ThreadLocal<>();

    private final ServerLevel level;
    private final Region<RegionTickData> region;
    private final RegionWorldData worldData;
    private final List<Runnable> releases = new ArrayList<>();

    private WorldTickContext(ServerLevel level, Region<RegionTickData> region, RegionWorldData worldData) {
        this.level = level;
        this.region = region;
        this.worldData = worldData;
    }

    public static WorldTickContext enter(ServerLevel level, Region<RegionTickData> region, RegionWorldData worldData) {
        WorldTickContext context = new WorldTickContext(level, region, worldData);
        CURRENT.set(context);
        return context;
    }

    public void exit() {
        releases.forEach(Runnable::run);
        CURRENT.remove();
    }

    public void keep(Runnable release) {
        releases.add(release);
    }

    public static WorldTickContext current() {
        return CURRENT.get();
    }

    public static RegionWorldData activeFor(ServerLevel level) {
        WorldTickContext context = CURRENT.get();
        return context != null && context.level == level ? context.worldData : null;
    }

    public static boolean ownsChunk(ServerLevel level, int chunkX, int chunkZ) {
        WorldTickContext context = CURRENT.get();
        return context != null && context.level == level && context.region.owns(chunkX, chunkZ);
    }

    public ServerLevel level() {
        return level;
    }

    public Region<RegionTickData> region() {
        return region;
    }
}
