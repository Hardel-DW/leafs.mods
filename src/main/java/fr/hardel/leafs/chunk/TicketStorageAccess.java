package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import fr.hardel.leafs.chunk.ticket.TicketTimeoutIndex;

/** Implemented onto {@code TicketStorage} by mixin: what its tickets feed. */
public interface TicketStorageAccess {
    TicketGraphs leafs$graphs();

    TicketTimeoutIndex leafs$timeouts();

    /** Bound with the chunk system, whose region sections shard the index. */
    void leafs$bindTimeouts(TicketTimeoutIndex timeouts);
}
