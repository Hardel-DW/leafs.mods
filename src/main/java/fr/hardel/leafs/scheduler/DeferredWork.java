package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ownership.OwnershipViolationException;

import java.util.function.BooleanSupplier;

// One piece of work for the owner of a chunk: reason, revalidation, retry budget. Inline when already there, dropped when revalidation fails or the budget runs out.
public record DeferredWork(
    int chunkX,
    int chunkZ,
    DeferReason reason,
    BooleanSupplier revalidation,
    Runnable task,
    int retryBudget,
    int attempt,
    DeferStats stats
) {

    public static DeferredWork owner(DeferReason reason, DeferStats stats, int chunkX, int chunkZ, Runnable task) {
        return new DeferredWork(chunkX, chunkZ, reason, () -> true, task, 0, 0, stats);
    }

    // The "entity still alive, player still connected" test, checked at the destination.
    public DeferredWork validIf(BooleanSupplier check) {
        return new DeferredWork(chunkX, chunkZ, reason, check, task, retryBudget, attempt, stats);
    }

    // Attempts read present chunks only, an ABSENT refusal re-submits on delivery; past the budget the work is dropped.
    public DeferredWork degraded(int budget) {
        return new DeferredWork(chunkX, chunkZ, reason, revalidation, task, budget, attempt, stats);
    }

    // Inline when the caller already owns the chunk, queued otherwise; true means deferred, so the injector cancels vanilla. The deferral counts here only, a replay is a retry.
    public boolean submit(DeferredTransports transports) {
        if (transports.owns(chunkX, chunkZ)) {
            execute(transports);

            return false;
        }

        stats.countDeferral(reason);
        enqueue(transports);

        return true;
    }

    private void enqueue(DeferredTransports transports) {
        transports.toOwner(chunkX, chunkZ, () -> execute(transports));
    }

    private void execute(DeferredTransports transports) {
        if (!revalidation.getAsBoolean()) {
            stats.countDrop(reason);

            return;
        }

        if (retryBudget == 0) {
            task.run();

            return;
        }

        if (attempt >= retryBudget) {
            stats.countDrop(reason);
            Leafs.LOGGER.warn("{} dropped after {} refused attempts, its chunks never arrived", reason, attempt);

            return;
        }

        try {
            transports.runDegraded(task);
        } catch (OwnershipViolationException refusal) {
            if (refusal.kind() != OwnershipViolationException.Kind.ABSENT) {
                throw refusal;
            }

            // The retry queues even from inside the destination: waiting for the delivery lets the pool load.
            stats.countRetry(reason);
            DeferredWork retry = new DeferredWork(chunkX, chunkZ, reason, revalidation, task, retryBudget, attempt + 1, stats);
            if (refusal.readiness() == null) {
                retry.enqueue(transports);
                return;
            }

            refusal.readiness().whenComplete((result, failure) -> retry.enqueue(transports));
        }
    }
}
