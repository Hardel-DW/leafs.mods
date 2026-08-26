package fr.hardel.leafs.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DegradedChunkReadsTest {

    /** 2026-08-20: the portal search is degraded but the teleport must run raw, or a refusal cuts the move mid-way. */
    @Test
    void escapeRunsRawAndRestoresTheScope() {
        DegradedChunkReads.run(() -> {
            assertTrue(DegradedChunkReads.active());
            DegradedChunkReads.escape(() -> assertFalse(DegradedChunkReads.active(), "the escaped block must read raw"));
            assertTrue(DegradedChunkReads.active(), "the scope must survive the escape");
        });

        assertFalse(DegradedChunkReads.active());
    }
}
