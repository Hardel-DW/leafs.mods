package fr.hardel.leafs.ownership;

/** A thread touched state it does not own. Thrown by dev-mode checks so corruption dies at the boundary. */
public final class OwnershipViolationException extends IllegalStateException {
    OwnershipViolationException(String message) {
        super(message);
    }
}
