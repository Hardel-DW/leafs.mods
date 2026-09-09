package fr.hardel.leafs.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Vanilla blocks the global thread at the flip until the spawn entities load; here it is a completion condition of the configuration phase, the owners deliver meanwhile. */
public final class SpawnEntityWait {

    private SpawnEntityWait() {
    }

    public static boolean shouldHold(ServerLevel level, Vec3 position, int radius) {
        return ChunkPos.rangeClosed(ChunkPos.containing(BlockPos.containing(position)), radius).anyMatch(chunk -> !level.areEntitiesLoaded(chunk.pack()));
    }
}
