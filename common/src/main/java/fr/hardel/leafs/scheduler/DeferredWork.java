package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BooleanSupplier;

public record DeferredWork(ChunkOwners owners, DeferStats stats, int chunkX, int chunkZ, DeferReason reason, BooleanSupplier revalidation, Runnable task) {

    public static DeferredWork owner(ServerLevel level, DeferReason reason, int chunkX, int chunkZ, Runnable task) {
        DeferStats stats = TickingManager.of(level.getServer()).metrics().deferStats();
        return new DeferredWork(LevelChunks.of(level).owners(), stats, chunkX, chunkZ, reason, () -> true, task);
    }

    public DeferredWork validIf(BooleanSupplier check) {
        return new DeferredWork(owners, stats, chunkX, chunkZ, reason, check, task);
    }

    public boolean submit() {
        if (owners.submit(chunkX, chunkZ, Work.GAME, this::execute)) {
            return false;
        }

        stats.countDeferral(reason);
        return true;
    }

    public void later() {
        owners.later(chunkX, chunkZ, Work.GAME, this::execute);
        stats.countDeferral(reason);
    }

    private void execute() {
        if (!revalidation.getAsBoolean()) {
            stats.countDrop(reason);
            return;
        }

        task.run();
    }
}
