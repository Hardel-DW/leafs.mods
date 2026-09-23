package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

public final class LeafsTicketTypes {
    public static final Identifier DEMAND = Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "demand");
    public static final TicketType demand = new TicketType(0, TicketType.FLAG_LOADING);

    private LeafsTicketTypes() {}
}
