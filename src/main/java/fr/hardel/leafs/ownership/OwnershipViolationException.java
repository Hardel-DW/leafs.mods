package fr.hardel.leafs.ownership;

/**
 * The chunk contract's typed refusal. ABSENT: the chunk is not loaded, a demand ticket has been
 * filed and a later attempt finds it. FOREIGN: the chunk is loaded but another region owns it, and
 * no ticket is filed because one would pin a holder that owner controls. Both are absorbed by
 * {@link TickGuard} at the nearest tick unit, never at the call site.
 */
public final class OwnershipViolationException extends RuntimeException {
    public enum Kind {
        ABSENT,
        FOREIGN
    }

    private final Kind kind;

    public OwnershipViolationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
