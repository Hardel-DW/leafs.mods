package fr.hardel.leafs.chunk;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.leafs.chunk.ticket.TicketGraphs;
import fr.hardel.leafs.chunk.ticket.TicketTimeoutIndex;
import fr.hardel.leafs.ticking.TickEpochs;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class TicketStorageActivityTest {
    private static final int SECTION_SHIFT = 2;
    private static final long FORCED_CHUNK = ChunkPos.pack(3, -7);
    private static final long TIMED_CHUNK = ChunkPos.pack(-20, 11);
    private static final TicketType TIMED = new TicketType(1L, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED);

    @Test
    void theActivityReadNeverWaitsForATicketWriter() {
        TicketStorage storage = new TicketStorage();
        bind(storage);
        storage.addTicket(FORCED_CHUNK, forced());
        CompletableFuture<Void> held = new CompletableFuture<>();
        CompletableFuture<Void> released = new CompletableFuture<>();
        Thread.ofPlatform().daemon().start(() -> {
            synchronized (storage) {
                held.complete(null);
                released.join();
            }
        });

        held.join();
        Boolean active = CompletableFuture.supplyAsync(storage::shouldKeepDimensionActive).completeOnTimeout(null, 5, TimeUnit.SECONDS).join();
        released.complete(null);
        assertEquals(Boolean.TRUE, active, "the activity read waited for the thread writing a ticket");
    }

    @Test
    void theActivityStaysExactThroughEveryPathATicketTakes() {
        TicketStorage storage = new TicketStorage();
        TicketTimeoutIndex timeouts = bind(storage);
        for (int x = 0; x < 64; x++) {
            storage.addTicket(ChunkPos.pack(x, 0), new Ticket(TicketType.PLAYER_LOADING, ChunkMap.FORCED_TICKET_LEVEL));
        }

        assertFalse(storage.shouldKeepDimensionActive(), "loading tickets alone keep the dimension active");
        storage.addTicket(FORCED_CHUNK, forced());
        storage.addTicket(FORCED_CHUNK, forced());
        storage.removeTicket(FORCED_CHUNK, forced());
        assertFalse(storage.shouldKeepDimensionActive(), "a ticket added twice counts twice");
        storage.addTicket(TIMED_CHUNK, timed());
        assertTrue(storage.shouldKeepDimensionActive(), "a timed ticket that keeps the dimension active does not count");
        timeouts.purge(section -> section == sectionOf(TIMED_CHUNK));
        timeouts.purge(section -> section == sectionOf(TIMED_CHUNK));
        assertFalse(storage.shouldKeepDimensionActive(), "a ticket timed out by its section still counts");
        storage.addTicket(TIMED_CHUNK, timed());
        storage.purgeStaleTickets(null);
        storage.purgeStaleTickets(null);
        assertFalse(storage.shouldKeepDimensionActive(), "a ticket purged by the vanilla sweep still counts");
        storage.addTicket(FORCED_CHUNK, forced());
        storage.replaceTicketLevelOfType(ChunkMap.FORCED_TICKET_LEVEL - 1, TicketType.FORCED);
        assertTrue(storage.shouldKeepDimensionActive(), "a ticket whose level was replaced no longer counts");
        storage.deactivateTicketsOnClosing();
        assertFalse(storage.shouldKeepDimensionActive(), "a deactivated ticket still counts");
        storage.activateAllDeactivatedTickets();
        assertTrue(storage.shouldKeepDimensionActive(), "a reactivated ticket does not count");
        Tag saved = TicketStorage.CODEC.encodeStart(NbtOps.INSTANCE, storage).getOrThrow();
        TicketStorage loaded = TicketStorage.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        bind(loaded);
        assertFalse(loaded.shouldKeepDimensionActive(), "a saved ticket counts before the level activates it");
        loaded.activateAllDeactivatedTickets();
        assertTrue(loaded.shouldKeepDimensionActive(), "a saved forced ticket does not count once the level activates it");
    }

    private static TicketTimeoutIndex bind(TicketStorage storage) {
        TicketTimeoutIndex timeouts = new TicketTimeoutIndex(storage, null, SECTION_SHIFT);
        ((TicketStorageAccess) storage).leafs$bind(new TicketGraphs(new TickEpochs(1, () -> {})), timeouts);
        return timeouts;
    }

    private static Ticket forced() {
        return new Ticket(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL);
    }

    private static Ticket timed() {
        return new Ticket(TIMED, ChunkMap.FORCED_TICKET_LEVEL);
    }

    private static long sectionOf(long chunk) {
        return ChunkPos.pack(ChunkPos.getX(chunk) >> SECTION_SHIFT, ChunkPos.getZ(chunk) >> SECTION_SHIFT);
    }
}
