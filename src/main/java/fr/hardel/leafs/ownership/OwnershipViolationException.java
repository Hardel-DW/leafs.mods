package fr.hardel.leafs.ownership;

import java.util.concurrent.CompletableFuture;

// The contract's typed refusal. ABSENT: not loaded, a demand ticket is filed, readiness completes at delivery. FOREIGN: another region owns it, no ticket. Absorbed by TickGuard, never at call sites.
public final class OwnershipViolationException extends RuntimeException {
    public enum Kind {
        ABSENT,
        FOREIGN
    }

    private final Kind kind;
    private final transient CompletableFuture<?> readiness;

    public OwnershipViolationException(Kind kind, String message) {
        this(kind, message, null);
    }

    public OwnershipViolationException(Kind kind, String message, CompletableFuture<?> readiness) {
        super(message);
        this.kind = kind;
        this.readiness = readiness;
    }

    public Kind kind() {
        return kind;
    }

    public CompletableFuture<?> readiness() {
        return readiness;
    }
}
