package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.holder.ChunkWait;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.RegionTickData;
import net.minecraft.server.level.ServerLevel;

/** The region whose tick body runs on this thread, and its level. Absent on the server thread, where vanilla's own state serves. */
public final class WorldTickContext {
    private static final ThreadLocal<WorldTickContext> CURRENT = new ThreadLocal<>();

    private final ServerLevel level;
    private final Region<RegionTickData> region;
    private final RegionWorldData worldData;

    private WorldTickContext(ServerLevel level, Region<RegionTickData> region, RegionWorldData worldData) {
        this.level = level;
        this.region = region;
        this.worldData = worldData;
    }

    public static void enter(ServerLevel level, Region<RegionTickData> region, RegionWorldData worldData) {
        CURRENT.set(new WorldTickContext(level, region, worldData));
        ChunkWait.enterScope();
    }

    public static void exit() {
        ChunkWait.exitScope();
        CURRENT.remove();
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
