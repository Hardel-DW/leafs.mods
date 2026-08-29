package fr.hardel.leafs.chunk.propagator;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.world.level.ChunkPos;

import java.util.Iterator;

/** Replaces vanilla's SimulationChunkTracker: fed by the simulation listener, published as a concurrent map regions read without boxing. 33 means not simulated. A chunk entering or leaving simulation is what shapes the regions. */
public final class SimulationLevels extends LeafsTicketPropagator {

    public static final int NOT_SIMULATED = 33;

    private final AreaLock ticketLock = new AreaLock(SECTION_SHIFT);
    private final ConcurrentLong2ObjectMap<Byte> levels = new ConcurrentLong2ObjectMap<>();
    private volatile SimulationListener listener;

    /** Bound once the level's regions exist; before that nothing simulates. */
    public void listen(SimulationListener listener) {
        this.listener = listener;
    }

    /** Rides the listener call under the table monitor; a level of 33 or above simulates nothing anywhere. */
    public void feed(long chunkKey, int ticketLevel) {
        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        AreaLock.Node node = ticketLock.lock(chunkX, chunkZ, 0);
        try {
            if (ticketLevel >= NOT_SIMULATED) {
                removeSource(chunkX, chunkZ);
                return;
            }

            setSource(chunkX, chunkZ, Math.min(convertBetweenTicketLevels(ticketLevel), MAX_SOURCE_LEVEL));
        } finally {
            ticketLock.unlock(node);
        }
    }

    /** Any thread may drain; far apart sections drain in parallel, neighbouring ones serialize on the ticket area. */
    public void drain() {
        performUpdates(ticketLock);
    }

    /** The vanilla simulation level of the chunk, 33 when nothing simulates it; ChunkLevel decides the tick rights. */
    public int level(long chunkKey) {
        Byte level = levels.get(chunkKey);
        return level == null ? NOT_SIMULATED : level;
    }

    @Override
    protected void onLevelUpdates(Long2ByteLinkedOpenHashMap updates) {
        for (Iterator<Long2ByteMap.Entry> iterator = updates.long2ByteEntrySet().fastIterator(); iterator.hasNext(); ) {
            Long2ByteMap.Entry entry = iterator.next();
            int level = convertBetweenTicketLevels(entry.getByteValue());
            long key = entry.getLongKey();
            if (entry.getByteValue() == 0 || level >= NOT_SIMULATED) {
                if (levels.remove(key) != null) {
                    listener.unsimulated(ChunkPos.getX(key), ChunkPos.getZ(key));
                }

                continue;
            }

            if (levels.put(key, Byte.valueOf((byte) level)) == null) {
                listener.simulated(ChunkPos.getX(key), ChunkPos.getZ(key));
            }
        }
    }
}
