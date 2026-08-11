package fr.hardel.leafs.ownership;

import fr.hardel.leafs.Leafs;
import net.minecraft.ReportedException;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A tick unit that reaches past the region's loaded chunks refuses itself before
 * mutating anything; the guard skips that one unit for the tick instead of crashing the region.
 * Every other failure keeps vanilla's crash path.
 */
public final class TickGuard {

    private TickGuard() {
    }

    public static <T> void tickOrSkip(Consumer<T> tick, T target) {
        try {
            tick.accept(target);
        } catch (OwnershipViolationException exception) {
            Leafs.LOGGER.debug("Tick skipped for {}: {}", target, exception.getMessage());
        } catch (ReportedException exception) {
            OwnershipViolationException refusal = refusalIn(exception);
            if (refusal == null) {
                throw exception;
            }

            Leafs.LOGGER.debug("Tick skipped for {}: {}", target, refusal.getMessage());
        }
    }

    /** Vanilla wraps mid-tick failures into crash reports (ServerPlayer.doTick) before the guard sees them; the refusal hides in the cause chain. */
    private static OwnershipViolationException refusalIn(Throwable throwable) {
        for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof OwnershipViolationException refusal) {
                return refusal;
            }
        }

        return null;
    }

    /** For lookups whose vanilla contract already includes a not-found result: a refusal degrades onto it. */
    public static <T> T callOrNull(Supplier<T> call, Object target) {
        try {
            return call.get();
        } catch (OwnershipViolationException exception) {
            Leafs.LOGGER.debug("Lookup refused for {}: {}", target, exception.getMessage());
            return null;
        }
    }
}
