package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

/** Leafs' ticket types; the loader puts them in vanilla's registry when it opens it. */
public final class LeafsTicketTypes {
    public static final Identifier DEMAND = Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "demand");
    /** Lives exactly as long as the wait that posted it, so no timeout. */
    public static final TicketType demand = new TicketType(0, TicketType.FLAG_LOADING);

    private LeafsTicketTypes() {}
}
