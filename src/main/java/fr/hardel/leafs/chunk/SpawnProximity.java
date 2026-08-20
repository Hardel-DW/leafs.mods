package fr.hardel.leafs.chunk;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The direct replacement of vanilla's naturalSpawnChunkCounter graph: a player entering a chunk
 * marks the disk of radius 8 around it, leaving unmarks it. Readers are the regions' spawn census
 * and the spawning proximity checks; writers ride the call sites of vanilla's addPlayer and
 * removePlayer, which the level's ownership discipline already serializes. Each chunk carries two
 * refcounts in one int, players within the inscribed square of 5 high, players within 8 low.
 */
public final class SpawnProximity {

    private static final int SPAWN_RADIUS = 8;
    private static final int COVERED_MASK = 0xFFFF;
    private static final int CLOSE_UNIT = 1 << 16;

    private final ConcurrentHashMap<Long, Integer> counts = new ConcurrentHashMap<>();

    public void add(int chunkX, int chunkZ) {
        mark(chunkX, chunkZ, 1);
    }

    public void remove(int chunkX, int chunkZ) {
        mark(chunkX, chunkZ, -1);
    }

    /** True while any player is within Chebyshev distance 8, the census condition of the spawn pass. */
    public boolean covered(long chunkKey) {
        return counts.containsKey(chunkKey);
    }

    /** Vanilla's TriState: TRUE within the inscribed square, FALSE past 8, DEFAULT asks the exact euclidean test. */
    public TriState nearby(long chunkKey) {
        Integer packed = counts.get(chunkKey);
        if (packed == null) {
            return TriState.FALSE;
        }

        return packed >= CLOSE_UNIT ? TriState.TRUE : TriState.DEFAULT;
    }

    /** The census size vanilla divides the mob caps by, every chunk within 8 of a player. */
    public int coveredCount() {
        return counts.size();
    }

    /** A weakly consistent iteration of the covered chunks, vanilla's spawn candidate list. */
    public LongIterator coveredChunks() {
        return LongIterators.asLongIterator(counts.keySet().iterator());
    }

    private void mark(int chunkX, int chunkZ, int direction) {
        for (int dz = -SPAWN_RADIUS; dz <= SPAWN_RADIUS; dz++) {
            for (int dx = -SPAWN_RADIUS; dx <= SPAWN_RADIUS; dx++) {
                int close = Math.max(Math.abs(dx), Math.abs(dz)) <= NaturalSpawner.INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK ? CLOSE_UNIT : 0;
                int delta = direction * (close + 1);
                counts.compute(ChunkPos.pack(chunkX + dx, chunkZ + dz), (key, packed) -> {
                    int updated = (packed == null ? 0 : packed) + delta;
                    return (updated & COVERED_MASK) == 0 ? null : updated;
                });
            }
        }
    }
}
