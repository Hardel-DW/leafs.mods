package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.ownership.OwnershipViolationException;

import java.util.function.BooleanSupplier;

/**
 * One deferred piece of work, declared immutably: where it runs, why, whether its target is still
 * valid when it does, and how it retries. The engine owns the bookkeeping every deferral used to
 * copy: the inline rule (a thread already holding the destination's guarantees runs in place), the
 * target revalidation, the attempt counter, and the degraded-read retry with a synchronous last
 * attempt. Declarations carry no mutable state, so one built on a region thread re-submits from the
 * window thread with no publication concern.
 */
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

    /** The "entity still alive, player still connected, chunk still loaded" test, checked at the destination. */
    public DeferredWork validIf(BooleanSupplier check) {
        return new DeferredWork(destination, reason, check, task, retryBudget, attempt, stats);
    }

    /**
     * The portal contract, generalised: attempts under the budget run inside the degraded-read
     * scope and an ABSENT refusal re-submits; the attempt past the budget runs raw, the one place a
     * deliberate synchronous load is allowed.
     */
    public DeferredWork degradedWithSyncNet(int budget) {
        return new DeferredWork(destination, reason, revalidation, task, budget, attempt, stats);
    }

    /**
     * Dispatches: inline when the calling thread already holds the destination's guarantees, queued
     * through the matching transport otherwise. Returns whether the work was deferred, so an
     * injector knows whether to cancel the vanilla path.
     */
    public boolean submit(DeferredTransports transports) {
        if (alreadyThere(transports)) {
            execute(transports);

            return false;
        }

        enqueue(transports);

        return true;
    }

    private void enqueue(DeferredTransports transports) {
        switch (destination) {
            case Destination.Window _ -> transports.toWindow(reason, () -> execute(transports));
            case Destination.Serial _ -> transports.toSerial(reason, () -> execute(transports));
            case Destination.Owner(int chunkX, int chunkZ) -> transports.toOwner(reason, chunkX, chunkZ, () -> execute(transports));
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
            // A FOREIGN refusal under the window is a genuine bug: no region ticks there, so it surfaces.
            if (refusal.kind() != OwnershipViolationException.Kind.ABSENT) {
                throw refusal;
            }

            // The retry queues even from inside the destination: waiting for the next pass is what lets the pool deliver the chunk.
            stats.countRetry(reason);
            new DeferredWork(destination, reason, revalidation, task, retryBudget, attempt + 1, stats).enqueue(transports);
        }
    }
}
