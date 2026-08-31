package fr.hardel.leafs.chunk;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.FakeTransports;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A block write against the owner of its chunk. */
class BlockWriteRerouteTest {

    private final FakeTransports transports = new FakeTransports();
    private final List<BlockPos> written = new ArrayList<>();

    private boolean record(BlockPos pos) {
        written.add(pos);
        return true;
    }

    /** 2026-08-31: PortalForcer walks one mutable across the sixteen writes of the frame, so every mail landed wherever the cursor had moved. */
    @Test
    void aMailedWriteLandsWhereTheCallerAskedNotWhereItsCursorEndedUp() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(4, 70, 9);

        assertTrue(BlockWriteReroute.write(cursor, transports, this::record));
        cursor.set(4, 73, 9);
        transports.drainOwner();

        assertEquals(List.of(new BlockPos(4, 70, 9)), written);
        assertEquals(1, transports.stats.deferrals(DeferReason.BLOCK_WRITE).perMinute());
    }

    @Test
    void theOwnerWritesInlineAndKeepsVanillasAnswer() {
        transports.owner = true;

        assertTrue(BlockWriteReroute.write(new BlockPos(4, 70, 9), transports, this::record));

        assertEquals(List.of(new BlockPos(4, 70, 9)), written);
        assertEquals(0, transports.stats.deferrals(DeferReason.BLOCK_WRITE).perMinute(), "an inline write is not a deferral");
    }

    @Test
    void theMailGoesToTheChunkHoldingTheBlock() {
        BlockWriteReroute.write(new BlockPos(-17, 70, 33), transports, this::record);

        assertEquals(List.of(new ChunkPos(-2, 2)), transports.ownerChunks);
    }
}
