package fr.hardel.leafs.chunk;

import fr.hardel.firefly.schedule.LightExecutor;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class PoolLightExecutor implements LightExecutor {
    @Override
    public void execute(LightChunkGetter level, int chunkX, int chunkZ, int radius, Runnable task) {
        LevelChunks.of((ServerLevel) level.getLevel()).placement().onPool(ChunkTask.Kind.LIGHT, ChunkStatus.LIGHT, chunkX, chunkZ, radius, task);
    }
}
