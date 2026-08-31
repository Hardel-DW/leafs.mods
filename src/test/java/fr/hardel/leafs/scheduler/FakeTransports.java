package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferStats;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/** Recording transports: queued tasks pile up and run only when the test drains them. */
public final class FakeTransports implements DeferredTransports {

    public final List<Runnable> ownerQueue = new ArrayList<>();
    public final List<ChunkPos> ownerChunks = new ArrayList<>();
    public final DeferStats stats = new DeferStats();
    public boolean owner;

    @Override
    public boolean toOwner(int chunkX, int chunkZ, Runnable task) {
        if (owner) {
            task.run();
            return true;
        }

        ownerQueue.add(task);
        ownerChunks.add(new ChunkPos(chunkX, chunkZ));
        return false;
    }

    @Override
    public boolean owns(int chunkX, int chunkZ) {
        return owner;
    }

    @Override
    public DeferStats stats() {
        return stats;
    }

    /** Drains like the owner's pass: only what was queued before the drain started runs. */
    public void drainOwner() {
        List<Runnable> batch = List.copyOf(ownerQueue);
        ownerQueue.clear();
        batch.forEach(Runnable::run);
    }
}
