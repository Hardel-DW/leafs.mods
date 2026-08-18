package fr.hardel.leafs.ownership;

import fr.hardel.leafs.Leafs;
import net.minecraft.ReportedException;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A tick unit that reaches a chunk it may not touch refuses itself before mutating anything; the
 * guard skips that one unit for the tick instead of crashing the region. Every other failure keeps
 * vanilla's crash path.
 */
public final class TickGuard {

    private TickGuard() {
    }

    public static <T> void tickOrSkip(Consumer<T> tick, T target) {
        try {
            tick.accept(target);
        } catch (OwnershipViolationException exception) {
            Leafs.LOGGER.debug("Tick skipped for {} ({}): {}", target, exception.kind(), exception.getMessage());
        } catch (ReportedException exception) {
            OwnershipViolationException refusal = refusalIn(exception);
            if (refusal == null) {
                throw exception;
            }

            Leafs.LOGGER.debug("Tick skipped for {} ({}): {}", target, refusal.kind(), refusal.getMessage());
        }
    }

    /**
     * The scheduled-tick form, built once per drain so the loop pays no allocation per item:
     * vanilla fires a scheduled tick exactly once, so a refused one must re-queue instead of being
     * lost. {@code onRefusal} is that re-queue, and this is its only home.
     */
    public static <A, B> BiConsumer<A, B> guardingWithRetry(BiConsumer<A, B> action, BiConsumer<A, B> onRefusal) {
        return (a, b) -> {
            try {
                action.accept(a, b);
            } catch (OwnershipViolationException exception) {
                Leafs.LOGGER.debug("Tick refused at {} ({}): {}", a, exception.kind(), exception.getMessage());
                onRefusal.accept(a, b);
            } catch (ReportedException exception) {
                OwnershipViolationException refusal = refusalIn(exception);
                if (refusal == null)
                    throw exception;

                Leafs.LOGGER.debug("Tick refused at {} ({}): {}", a, refusal.kind(), refusal.getMessage());
                onRefusal.accept(a, b);
            }
        };
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
}
