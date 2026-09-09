package fr.hardel.leafs.chunk.pool;

import fr.hardel.leafs.chunk.pool.ChunkTask.Kind;
import fr.hardel.leafs.metrics.MinuteCounter;

/** Tasks parked behind a reservation, by their kind and the holder's: what one reservation space for light and generation alike costs. */
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

    public MinuteCounter of(Kind blocked, Kind holder) {
        return counts[blocked.ordinal()][holder.ordinal()];
    }

    public long total() {
        long total = 0;
        for (MinuteCounter[] row : counts) {
            for (MinuteCounter counter : row) {
                total += counter.total();
            }
        }

        return total;
    }
}
