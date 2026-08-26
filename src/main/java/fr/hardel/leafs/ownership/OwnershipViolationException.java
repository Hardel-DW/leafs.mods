package fr.hardel.leafs.ownership;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;

/**
 * The contract's typed refusal. ABSENT: not loaded, a demand ticket is filed, readiness completes at
 * delivery. FOREIGN: another owner holds the position, the refusal names it so the work can hop there.
 */
public final class OwnershipViolationException extends RuntimeException {
    public enum Kind {
        ABSENT,
        FOREIGN
    }

    private final Kind kind;
    private final transient CompletableFuture<?> readiness;
    private final transient ServerLevel foreignLevel;
    private final long foreignChunk;

    public OwnershipViolationException(Kind kind, String message) {
        this(kind, message, null);
    }

    public OwnershipViolationException(Kind kind, String message, CompletableFuture<?> readiness) {
        super(message);
        this.kind = kind;
        this.readiness = readiness;
        this.foreignLevel = null;
        this.foreignChunk = 0;
    }

    public OwnershipViolationException(String message, ServerLevel foreignLevel, ChunkPos foreignChunk) {
        super(message);
        this.kind = Kind.FOREIGN;
        this.readiness = null;
        this.foreignLevel = foreignLevel;
        this.foreignChunk = foreignChunk.pack();
    }

    public Kind kind() {
        return kind;
    }

    public CompletableFuture<?> readiness() {
        return readiness;
    }

    public ServerLevel foreignLevel() {
        return foreignLevel;
    }

    public ChunkPos foreignChunk() {
        return ChunkPos.unpack(foreignChunk);
    }
}
