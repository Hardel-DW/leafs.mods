package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;


public final class LeafsTicketTypes {
    private static final long DEMAND_TIMEOUT_TICKS = 300;

    public static TicketType hold;
    public static TicketType demand;

    private LeafsTicketTypes() {
    }

    public static void register() {
        hold = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "hold"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        demand = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "demand"),
            new TicketType(DEMAND_TIMEOUT_TICKS, TicketType.FLAG_LOADING));
    }
}
