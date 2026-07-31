package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;

/**
 * Leafs ticket types. HOLD keeps a chunk holder (and therefore its region) alive without loading the
 * chunk — the task-queue and teleport safety net. No timeout, no persistence: holds are runtime-only
 * and released explicitly.
 */
public final class LeafsTicketTypes {
    public static TicketType hold;

    private LeafsTicketTypes() {
    }

    public static void register() {
        hold = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "hold"), new TicketType(TicketType.NO_TIMEOUT, 0));
    }
}
