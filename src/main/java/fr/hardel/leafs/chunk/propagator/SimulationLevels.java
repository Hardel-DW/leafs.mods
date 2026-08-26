package fr.hardel.leafs.chunk.propagator;

import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.world.level.ChunkPos;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/** Replaces vanilla's SimulationChunkTracker: fed by the simulation listener, published as a concurrent map regions read lock-free. 33 means not simulated. */
public final class SimulationLevels extends LeafsTicketPropagator {

    public static final int NOT_SIMULATED = 33;

    private final AreaLock ticketLock = new AreaLock(SECTION_SHIFT);
    private final ConcurrentHashMap<Long, Byte> levels = new ConcurrentHashMap<>();

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
            if (entry.getByteValue() == 0 || level >= NOT_SIMULATED) {
                levels.remove(entry.getLongKey());
                continue;
            }

            levels.put(entry.getLongKey(), (byte) level);
        }
    }
}
