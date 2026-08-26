package fr.hardel.leafs.chunk.loader;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanilla sends a chunk only from the ticking promotion, so a stage the player must see has to reach it. */
class StageTicketsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static boolean reachesTheSendingPromotion(int stage) {
        return ChunkLevel.fullStatus(StageTickets.level(stage)).isOrAfter(FullChunkStatus.BLOCK_TICKING);
    }

    @Test
    void everyVisibleStageReachesTheSendingPromotion() {
        assertTrue(reachesTheSendingPromotion(StageTickets.GENERATED), "the view distance must be sendable");
        assertTrue(reachesTheSendingPromotion(StageTickets.TICK), "the simulation distance must be sendable");
    }

    @Test
    void theBorderRingOnlyPrefetches() {
        assertEquals(ChunkLevel.MAX_LEVEL, StageTickets.level(StageTickets.LOADED));
        assertFalse(reachesTheSendingPromotion(StageTickets.LOADED), "the ring outside the view must not promote a chunk");
    }
}
