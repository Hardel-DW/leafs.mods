package fr.hardel.leafs.chunk;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionChunkAccessTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final LevelHeightAccessor HEIGHT = new LevelHeightAccessor() {
        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    };

    /** 2026-08-04 overstress crash: hasChunk answered from the ticket level, the read path from presence; both must agree. */
    @Test
    void ticketEligibleHolderWithoutCompletedChunkReadsAbsent() {
        ChunkHolder holder = new ChunkHolder(new ChunkPos(0, 0), ChunkLevel.byStatus(ChunkStatus.FULL), HEIGHT, null,
            (pos, oldLevel, newLevel, setQueueLevel) -> {}, (pos, borderOnly) -> List.of());

        assertTrue(holder.getTicketLevel() <= ChunkLevel.byStatus(ChunkStatus.FULL));
        assertNull(RegionChunkAccess.fullChunkOrNull(holder));
    }
}
