package fr.hardel.leafs.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Vanilla blocks the global thread at the flip until the spawn entities load; here it is a completion condition of the configuration phase. */
public final class SpawnEntityWait {

    private SpawnEntityWait() {
    }

    /** Empty server: nothing ticks the entity loads, holding would never resolve, the inline wait takes over. */
    public static boolean shouldHold(ServerLevel level, Vec3 position, int radius) {
        return level.getServer().getPlayerCount() > 0 && !entitiesLoaded(level, ChunkPos.containing(BlockPos.containing(position)), radius);
    }

    /** A satisfied wait is skipped entirely: its managed block would process entity loads the serial phase owns. */
    public static void skipSatisfiedWait(ServerLevel level, ChunkPos center, int radius, Runnable vanillaWait) {
        if (!entitiesLoaded(level, center, radius)) {
            vanillaWait.run();
        }
    }

    private static boolean entitiesLoaded(ServerLevel level, ChunkPos center, int radius) {
        return ChunkPos.rangeClosed(center, radius).allMatch(chunk -> level.areEntitiesLoaded(chunk.pack()));
    }
}
