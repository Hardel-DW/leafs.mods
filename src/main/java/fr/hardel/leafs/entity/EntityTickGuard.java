package fr.hardel.leafs.entity;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ownership.OwnershipViolationException;

import java.util.function.Consumer;

/**
 * Compromise #19: an entity tick that reaches past the region's loaded chunks refuses itself with an
 * {@code OwnershipViolationException} before mutating anything; the guard skips that entity for the
 * tick instead of crashing the region. Every other failure keeps vanilla's crash path.
 */
public final class EntityTickGuard {

    private EntityTickGuard() {
    }

    public static <T> void tickOrSkip(Consumer<T> tick, T entity) {
        try {
            tick.accept(entity);
        } catch (OwnershipViolationException exception) {
            Leafs.LOGGER.debug("Entity tick skipped for {}: {}", entity, exception.getMessage());
        }
    }
}
