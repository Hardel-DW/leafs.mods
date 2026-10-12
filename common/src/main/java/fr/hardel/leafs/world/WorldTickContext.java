package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.RegionTickData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WorldTickContext {
    private static final ThreadLocal<WorldTickContext> CURRENT = new ThreadLocal<>();
    private static final int SIDE = 4;

    private final ServerLevel level;
    private final Region<RegionTickData> region;
    private final RegionWorldData worldData;
    private final List<Runnable> releases = new ArrayList<>();
    private final LevelChunk[] lastChunks = new LevelChunk[SIDE * SIDE];

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

    public static RegionWorldData activeFor(Level level) {
        WorldTickContext context = CURRENT.get();
        return context != null && context.level == level ? context.worldData : null;
    }

    public @Nullable LevelChunk lastChunk(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = lastChunks[slot(chunkX, chunkZ)];
        return chunk != null && chunk.getPos().x() == chunkX && chunk.getPos().z() == chunkZ && chunk.getLevel() == level ? chunk : null;
    }

    public void remember(LevelChunk chunk) {
        lastChunks[slot(chunk.getPos().x(), chunk.getPos().z())] = chunk;
    }

    /** A chunk this thread unloads may be one it remembers. */
    public void forgetChunks() {
        Arrays.fill(lastChunks, null);
    }

    private static int slot(int chunkX, int chunkZ) {
        return (chunkZ & SIDE - 1) * SIDE + (chunkX & SIDE - 1);
    }

    public ServerLevel level() {
        return level;
    }

    public Region<RegionTickData> region() {
        return region;
    }
}
