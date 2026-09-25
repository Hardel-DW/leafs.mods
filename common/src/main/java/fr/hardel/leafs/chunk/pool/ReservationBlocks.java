package fr.hardel.leafs.chunk.pool;

import fr.hardel.leafs.chunk.pool.ChunkTask.Kind;
import fr.hardel.leafs.metrics.MinuteCounter;

public final class ReservationBlocks {
    private final MinuteCounter[][] counts = new MinuteCounter[Kind.values().length][Kind.values().length];

    ReservationBlocks() {
        for (MinuteCounter[] row : counts) {
            for (int holder = 0; holder < row.length; holder++) {
                row[holder] = new MinuteCounter();
            }
        }
    }

    void count(ChunkTask blocked, ChunkTask holder) {
        of(blocked.kind(), holder.kind()).increment();
    }

    // Used by the Leafs Debug mod
    public MinuteCounter of(Kind blocked, Kind holder) {
        return counts[blocked.ordinal()][holder.ordinal()];
    }
}
