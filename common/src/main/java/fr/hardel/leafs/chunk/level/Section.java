package fr.hardel.leafs.chunk.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

final class Section {
    static final int SHIFT = 6;
    static final int SIZE = 1 << SHIFT;
    static final int MASK = SIZE - 1;
    private static final VarHandle LEVELS = MethodHandles.arrayElementVarHandle(byte[].class);

    final long key;
    final AtomicBoolean queued = new AtomicBoolean();
    private final int none;
    private final byte[] levels = new byte[SIZE * SIZE];
    private final byte[] sources = new byte[SIZE * SIZE];
    private final Short2LongOpenHashMap pending = new Short2LongOpenHashMap();
    private int occupied;
    private boolean retired;

    Section(long key, int none) {
        this.key = key;
        this.none = none;
        Arrays.fill(levels, (byte) none);
        Arrays.fill(sources, (byte) none);
    }

    static int index(int chunkX, int chunkZ) {
        return (chunkZ & MASK) << SHIFT | (chunkX & MASK);
    }

    static long keyOf(int chunkX, int chunkZ) {
        return ChunkPos.pack(chunkX >> SHIFT, chunkZ >> SHIFT);
    }

    boolean post(int index, int level, long written) {
        synchronized (pending) {
            if (retired) {
                return false;
            }

            pending.put((short) index, written << Byte.SIZE | level);
            return true;
        }
    }

    // Decreases pass at once, increases once every tick open at their write has ended. Returns the oldest held write, or Long.MAX_VALUE.
    long takeVisible(long oldestOpen, Long2ByteMap changes) {
        synchronized (pending) {
            for (ObjectIterator<Short2LongMap.Entry> iterator = pending.short2LongEntrySet().fastIterator(); iterator.hasNext(); ) {
                Short2LongMap.Entry entry = iterator.next();
                int index = entry.getShortKey() & 0xFFFF;
                int level = (int) (entry.getLongValue() & 0xFF);
                if (level <= sources[index] || entry.getLongValue() >> Byte.SIZE < oldestOpen) {
                    changes.put(chunkKey(index), (byte) level);
                    iterator.remove();
                }
            }

            long oldestHeld = Long.MAX_VALUE;
            for (LongIterator iterator = pending.values().iterator(); iterator.hasNext(); ) {
                oldestHeld = Math.min(oldestHeld, iterator.nextLong() >> Byte.SIZE);
            }

            return oldestHeld;
        }
    }

    boolean retire() {
        synchronized (pending) {
            if (occupied > 0 || !pending.isEmpty() || queued.get()) {
                return false;
            }

            retired = true;
            return true;
        }
    }

    int level(int index) {
        return (byte) LEVELS.getAcquire(levels, index);
    }

    void forEachAtMost(int level, LongConsumer consumer) {
        for (int index = 0; index < levels.length; index++) {
            if (level(index) <= level) {
                consumer.accept(chunkKey(index));
            }
        }
    }

    private long chunkKey(int index) {
        return ChunkPos.pack((ChunkPos.getX(key) << SHIFT) + (index & MASK), (ChunkPos.getZ(key) << SHIFT) + (index >> SHIFT));
    }

    void publish(int index, int level) {
        int before = levels[index];
        occupied += (before == none ? 1 : 0) - (level == none ? 1 : 0);
        LEVELS.setRelease(levels, index, (byte) level);
    }

    int source(int index) {
        return sources[index];
    }

    void setSource(int index, int level) {
        sources[index] = (byte) level;
    }
}
