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

    /**
     * The overstress crash of 2026-08-04: vanilla {@code ServerChunkCache.hasChunk} answers from the
     * ticket level, which only says the chunk is DUE; the region read path answers from presence.
     * Both surfaces must agree for a region worker or a correct hasChunk-then-read sequence crashes.
     */
    @Test
    void ticketEligibleHolderWithoutCompletedChunkReadsAbsent() {
        ChunkHolder holder = new ChunkHolder(new ChunkPos(0, 0), ChunkLevel.byStatus(ChunkStatus.FULL), HEIGHT, null,
            (pos, oldLevel, newLevel, setQueueLevel) -> {}, (pos, borderOnly) -> List.of());

        assertTrue(holder.getTicketLevel() <= ChunkLevel.byStatus(ChunkStatus.FULL));
        assertNull(RegionChunkAccess.fullChunkOrNull(holder));
    }
}
