package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

/**
 * Leafs ticket types. HOLD keeps a chunk holder - and therefore its region - alive without simulating
 * the chunk: the task-queue and teleport safety net. LOADING is what makes the loading tracker create
 * the holder at all (a flagless ticket is invisible to both trackers); KEEP_DIMENSION_ACTIVE stops an
 * otherwise empty destination dimension from going idle under a pending arrival, exactly like vanilla
 * PORTAL and ENDER_PEARL. No SIMULATION - a held chunk must not start ticking mobs nobody is near - no
 * timeout and no persistence: holds are runtime-only and released explicitly.
 */
public final class LeafsTicketTypes {
    public static TicketType hold;

    private LeafsTicketTypes() {
    }

    public static void register() {
        hold = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "hold"),
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    }
}
