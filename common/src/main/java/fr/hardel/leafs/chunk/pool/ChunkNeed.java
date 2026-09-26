package fr.hardel.leafs.chunk.pool;

import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public record ChunkNeed(int chunkX, int chunkZ, ChunkDependencies around) {
    public static ChunkNeed of(ChunkPyramid pyramid, int chunkX, int chunkZ, ChunkStatus status) {
        return new ChunkNeed(chunkX, chunkZ, pyramid.getStepTo(status).accumulatedDependencies());
    }

    public int radius() {
        return around.getRadius();
    }

    public boolean covers(int chunkX, int chunkZ, ChunkStatus status) {
        int distance = Math.max(Math.abs(chunkX - this.chunkX), Math.abs(chunkZ - this.chunkZ));
        return distance == 0 || distance <= around.getRadius() && !status.isAfter(around.get(distance));
    }
}
