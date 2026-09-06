package fr.hardel.leafs.chunk.level;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.shorts.Short2ByteOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** 64 by 64 chunks of one graph: the settled levels, the sources, and the source changes not yet propagated. Exists while a level does, retired once empty. */
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
    private final Short2ByteOpenHashMap pending = new Short2ByteOpenHashMap();
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
        return ((chunkX >> SHIFT) & 0xFFFFFFFFL) | (((long) (chunkZ >> SHIFT) & 0xFFFFFFFFL) << 32);
    }

    /** Last write wins until the next drain takes the batch. False once retired: the writer asks the graph for the section again. */
    boolean post(int index, int level) {
        synchronized (pending) {
            if (retired) {
                return false;
            }

            pending.put((short) index, (byte) level);
            return true;
        }
    }

    Short2ByteOpenHashMap takePending() {
        synchronized (pending) {
            Short2ByteOpenHashMap batch = pending.clone();
            pending.clear();
            return batch;
        }
    }

    /** Under the drain's locks, once its levels are all gone: nothing pending and nothing queued, or a source posted meanwhile would be lost. */
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
        int originX = (int) key << SHIFT;
        int originZ = (int) (key >> 32) << SHIFT;
        for (int index = 0; index < levels.length; index++) {
            if (level(index) <= level) {
                consumer.accept(ChunkPos.pack(originX + (index & MASK), originZ + (index >> SHIFT)));
            }
        }
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
