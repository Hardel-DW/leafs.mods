package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BooleanSupplier;

/** One piece of work for the owner of a chunk: reason, revalidation at the destination. Dropped when revalidation fails. */
public record DeferredWork(ChunkOwners owners, DeferStats stats, int chunkX, int chunkZ, DeferReason reason, BooleanSupplier revalidation, Runnable task) {

    public static DeferredWork owner(ServerLevel level, DeferReason reason, int chunkX, int chunkZ, Runnable task) {
        DeferStats stats = TickingManager.of(level.getServer()).metrics().deferStats();
        return new DeferredWork(LevelChunks.of(level).owners(), stats, chunkX, chunkZ, reason, () -> true, task);
    }

    /** The "entity still alive, player still connected" test, checked at the destination. */
    public DeferredWork validIf(BooleanSupplier check) {
        return new DeferredWork(owners, stats, chunkX, chunkZ, reason, check, task);
    }

    /** True means the work left for the chunk's owner, a region or the server thread, so the injector cancels vanilla; work that ran here counts nothing. */
    public boolean submit() {
        if (owners.submit(chunkX, chunkZ, Work.GAME, this::execute)) {
            return false;
        }

        stats.countDeferral(reason);
        return true;
    }

    private void execute() {
        if (!revalidation.getAsBoolean()) {
            stats.countDrop(reason);
            return;
        }

        task.run();
    }
}
