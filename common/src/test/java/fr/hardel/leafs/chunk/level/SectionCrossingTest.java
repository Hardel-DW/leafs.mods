package fr.hardel.leafs.chunk.level;

import fr.hardel.leafs.ticking.TickEpochs;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SectionCrossingTest {
    private static final int LEVELS = 34;
    private static final int VIEW = 32;

    @Test
    void aPlayerCrossingASectionBorderKeepsItsView() {
        ChunkLevels players = new ChunkLevels(LEVELS, new TickEpochs(0, () -> { }));
        players.setSource(63, 0, 0);
        players.drain((_, _, _) -> {});

        LongOpenHashSet lost = new LongOpenHashSet();
        players.setSource(63, 0, players.none());
        players.setSource(64, 0, 0);
        players.drain((key, old, now) -> {
            boolean stillInView = Math.max(Math.abs(ChunkPos.getX(key) - 64), Math.abs(ChunkPos.getZ(key))) <= VIEW - 1;
            if (stillInView && old <= VIEW && now > VIEW) {
                lost.add(key);
            }
        });

        assertEquals(0, lost.size(), "chunks that stay in view but lost their player level for a moment");
    }
}
