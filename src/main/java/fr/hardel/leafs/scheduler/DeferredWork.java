package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;

import java.util.function.BooleanSupplier;

/** One piece of work for the owner of a chunk: reason, revalidation at the destination. Dropped when revalidation fails. */
public record DeferredWork(int chunkX, int chunkZ, DeferReason reason, BooleanSupplier revalidation, Runnable task, DeferStats stats) {

    public static DeferredWork owner(DeferReason reason, DeferStats stats, int chunkX, int chunkZ, Runnable task) {
        return new DeferredWork(chunkX, chunkZ, reason, () -> true, task, stats);
    }

    /** The "entity still alive, player still connected" test, checked at the destination. */
    public DeferredWork validIf(BooleanSupplier check) {
        return new DeferredWork(chunkX, chunkZ, reason, check, task, stats);
    }

    /** True means the work is mail for a region that ticks the chunk, so the injector cancels vanilla; work that ran here counts nothing. */
    public boolean submit(DeferredTransports transports) {
        if (transports.toOwner(chunkX, chunkZ, this::execute)) {
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
