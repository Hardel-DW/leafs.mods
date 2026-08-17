package fr.hardel.leafs.chunk.core;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentChunkTableTest {

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

    private static ChunkHolder holder(int x, int z) {
        return new ChunkHolder(new ChunkPos(x, z), ChunkLevel.byStatus(ChunkStatus.FULL), HEIGHT, null,
            (pos, oldLevel, newLevel, setQueueLevel) -> {}, (pos, borderOnly) -> List.of());
    }

    @Test
    void pointOperationsAndViewsReadTheSameBacking() {
        ConcurrentChunkTable table = new ConcurrentChunkTable();
        ChunkHolder holder = holder(3, 4);
        long key = ChunkPos.pack(3, 4);

        assertNull(table.put(key, holder));
        assertSame(holder, table.get(key));
        assertTrue(table.containsKey(key));
        assertEquals(1, table.size());
        assertEquals(1, table.values().size());
        assertTrue(table.values().contains(holder));
        assertEquals(1, table.long2ObjectEntrySet().size());
        assertSame(holder, table.remove(key));
        assertTrue(table.isEmpty());
    }

    /** The superclass storage is empty, so an undelegated surface must fail instead of answering from it. */
    @Test
    void unsupportedSurfacesThrowInsteadOfAnsweringEmpty() {
        ConcurrentChunkTable table = new ConcurrentChunkTable();
        table.put(ChunkPos.pack(1, 1), holder(1, 1));

        assertThrows(UnsupportedOperationException.class, table::keySet);
        assertThrows(UnsupportedOperationException.class, () -> table.long2ObjectEntrySet().first());
    }

    /** The promotion step became this flag: it must report each holder churn exactly once. */
    @Test
    void dirtyFlagConsumesOncePerChurn() {
        ConcurrentChunkTable table = new ConcurrentChunkTable();
        assertFalse(table.consumeDirty());
        table.put(ChunkPos.pack(0, 0), holder(0, 0));
        assertTrue(table.consumeDirty());
        assertFalse(table.consumeDirty());
        table.remove(ChunkPos.pack(0, 0));
        assertTrue(table.consumeDirty());
        assertFalse(table.consumeDirty());
    }

    /** Both vanilla fields point at the same instance; clone must not fork the table. */
    @Test
    void cloneReturnsTheSameInstance() {
        ConcurrentChunkTable table = new ConcurrentChunkTable();
        assertSame(table, table.clone());
    }
}
