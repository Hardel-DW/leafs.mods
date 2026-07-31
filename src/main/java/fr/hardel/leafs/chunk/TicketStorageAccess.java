package fr.hardel.leafs.chunk;

import java.util.concurrent.Executor;

/** Implemented onto {@code TicketStorage} by mixin: binds the level's chunk-thread executor. */
public interface TicketStorageAccess {

    void leafs$bindChunkExecutor(Executor executor);
}
