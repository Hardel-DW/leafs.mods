package fr.hardel.leafs.ownership;

import fr.hardel.leafs.Leafs;
import net.minecraft.ReportedException;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** A refused item skips its own tick instead of crashing the region; every other failure keeps vanilla's crash path. */
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

    /** Scheduled-tick form, built once per drain. Vanilla fires a scheduled tick once, so a refused one re-queues through {@code onRefusal}. */
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

    /** Vanilla wraps mid-tick failures into crash reports before the guard sees them; the refusal hides in the cause chain. */
    public static boolean isRefusal(Throwable throwable) {
        return throwable instanceof OwnershipViolationException || refusalIn(throwable) != null;
    }

    private static OwnershipViolationException refusalIn(Throwable throwable) {
        for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof OwnershipViolationException refusal) {
                return refusal;
            }
        }

        return null;
    }
}
