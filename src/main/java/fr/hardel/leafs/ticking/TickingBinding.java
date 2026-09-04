package fr.hardel.leafs.ticking;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.scheduler.DeferredTransports;
import net.minecraft.server.level.ServerLevel;

/** The per-level implementation of the deferral transports, resolved per call because the level activates later. */
public record TickingBinding(ServerLevel level) implements DeferredTransports {
    public static DeferredTransports of(ServerLevel level) {
        return new TickingBinding(level);
    }

    @Override
    public boolean toOwner(int chunkX, int chunkZ, Runnable task) {
        return owners().submit(chunkX, chunkZ, task);
    }

    @Override
    public boolean owns(int chunkX, int chunkZ) {
        return owners().holds(chunkX, chunkZ);
    }

    @Override
    public DeferStats stats() {
        return TickingManager.of(level.getServer()).metrics().deferStats();
    }

    private ChunkOwners owners() {
        return LevelChunks.of(level).owners();
    }
}
