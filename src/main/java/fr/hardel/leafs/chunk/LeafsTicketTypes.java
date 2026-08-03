package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;


public final class LeafsTicketTypes {
    public static TicketType hold;

    private LeafsTicketTypes() {
    }

    public static void register() {
        hold = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "hold"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    }
}
