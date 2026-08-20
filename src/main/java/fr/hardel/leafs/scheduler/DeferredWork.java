package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ownership.OwnershipViolationException;

import java.util.function.BooleanSupplier;

// One deferred piece of work, immutable: destination, reason, revalidation, retry policy. The engine owns the bookkeeping: run inline when already at the destination, drop when revalidation fails, retry degraded with a synchronous last attempt.
public record DeferredWork(
    Destination destination,
    DeferReason reason,
    BooleanSupplier revalidation,
    Runnable task,
    int retryBudget,
    int attempt,
    DeferStats stats
) {

    public sealed interface Destination {

        record Window() implements Destination {
        }

        record Serial() implements Destination {
        }

        record Owner(int chunkX, int chunkZ) implements Destination {
        }
    }

    public static DeferredWork window(DeferReason reason, DeferStats stats, Runnable task) {
        return new DeferredWork(new Destination.Window(), reason, () -> true, task, 0, 0, stats);
    }

    public static DeferredWork serial(DeferReason reason, DeferStats stats, Runnable task) {
        return new DeferredWork(new Destination.Serial(), reason, () -> true, task, 0, 0, stats);
    }

    public static DeferredWork owner(DeferReason reason, DeferStats stats, int chunkX, int chunkZ, Runnable task) {
        return new DeferredWork(new Destination.Owner(chunkX, chunkZ), reason, () -> true, task, 0, 0, stats);
    }

    // The "entity still alive, player still connected" test, checked at the destination.
    public DeferredWork validIf(BooleanSupplier check) {
        return new DeferredWork(destination, reason, check, task, retryBudget, attempt, stats);
    }

    // Attempts under the budget run degraded and an ABSENT refusal re-submits; the attempt past it runs raw, the one allowed synchronous load.
    public DeferredWork degradedWithSyncNet(int budget) {
        return new DeferredWork(destination, reason, revalidation, task, budget, attempt, stats);
    }

    // Inline when the caller already holds the destination's guarantees, queued otherwise; true means deferred, so the injector cancels vanilla. The deferral counts here only, a replay is a retry.
    public boolean submit(DeferredTransports transports) {
        if (alreadyThere(transports)) {
            execute(transports);

            return false;
        }

        stats.countDeferral(reason);
        enqueue(transports);

        return true;
    }

    private void enqueue(DeferredTransports transports) {
        switch (destination) {
            case Destination.Window _ -> transports.toWindow(() -> execute(transports));
            case Destination.Serial _ -> transports.toSerial(() -> execute(transports));
            case Destination.Owner(int chunkX, int chunkZ) -> transports.toOwner(chunkX, chunkZ, () -> execute(transports));
        }
    }

    private boolean alreadyThere(DeferredTransports transports) {
        return switch (destination) {
            case Destination.Window _ -> transports.holdsWindow();
            case Destination.Serial _ -> transports.holdsSerial();
            case Destination.Owner(int chunkX, int chunkZ) -> transports.owns(chunkX, chunkZ);
        };
    }

    private void execute(DeferredTransports transports) {
        if (!revalidation.getAsBoolean()) {
            stats.countDrop(reason);

            return;
        }

        if (attempt >= retryBudget) {
            task.run();

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
            DeferredWork retry = new DeferredWork(destination, reason, revalidation, task, retryBudget, attempt + 1, stats);
            if (refusal.readiness() == null) {
                retry.enqueue(transports);
                return;
            }

            refusal.readiness().whenComplete((result, failure) -> retry.enqueue(transports));
        }
    }
}
