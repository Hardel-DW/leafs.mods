package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;


public final class LeafsTicketTypes {
    private static final long DEMAND_TIMEOUT_TICKS = 300;
    private static final long VIEW_DELAY_TICKS = 20;

    public static TicketType hold;
    public static TicketType demand;
    public static TicketType view;
    public static TicketType viewTick;
    public static TicketType viewDelayed;
    public static TicketType viewTickDelayed;

    private LeafsTicketTypes() {
    }

    public static void register() {
        hold = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "hold"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        demand = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "demand"),
            new TicketType(DEMAND_TIMEOUT_TICKS, TicketType.FLAG_LOADING));
        view = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "view"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        viewTick = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "view_tick"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        viewDelayed = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "view_delayed"),
            new TicketType(VIEW_DELAY_TICKS, TicketType.FLAG_LOADING | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED));
        viewTickDelayed = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "view_tick_delayed"),
            new TicketType(VIEW_DELAY_TICKS, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED));
    }
}
