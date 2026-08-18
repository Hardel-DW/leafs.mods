package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ServerLevel;

/** Binds the owning level onto the ticket storage, which vanilla constructs level-blind as saved data. */
public interface TicketStorageAccess {

    void leafs$bindLevel(ServerLevel level);

    /** Null until the first timeout ticket lands; the purgers treat null as an empty index. */
    TicketTimeoutIndex leafs$timeouts();
}
