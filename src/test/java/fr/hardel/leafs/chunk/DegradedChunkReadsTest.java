package fr.hardel.leafs.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DegradedChunkReadsTest {

    /**
     * 2026-08-20: the portal task searches under the degraded scope but must teleport raw, or a
     * refusal inside vanilla's teleport would cut the move after the entity left its origin.
     */
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
