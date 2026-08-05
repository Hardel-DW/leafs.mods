package fr.hardel.leafs.ownership;

import fr.hardel.leafs.Leafs;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Compromise #19: a tick unit that reaches past the region's loaded chunks refuses itself before
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
        }
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
