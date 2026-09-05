package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

public final class LeafsTicketTypes {
    /** Lives exactly as long as the wait that posted it, so no timeout. */
    public static TicketType demand;

    private LeafsTicketTypes() {}

    public static void register() {
        demand = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "demand"), new TicketType(0, TicketType.FLAG_LOADING));
    }
}
