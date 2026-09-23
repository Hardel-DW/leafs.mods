package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import fr.hardel.leafs.chunk.ticket.TicketTimeoutIndex;

public interface TicketStorageAccess {
    TicketGraphs leafs$graphs();

    void leafs$bindTimeouts(TicketTimeoutIndex timeouts);
}
