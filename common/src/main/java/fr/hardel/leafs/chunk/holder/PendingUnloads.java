package fr.hardel.leafs.chunk.holder;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;

import java.util.concurrent.atomic.AtomicInteger;

public final class PendingUnloads extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {
    private final AtomicInteger teardowns = new AtomicInteger();

    /** A holder leaves the map when its teardown starts, and is on its way to the disk only once that teardown ends. */
    public Runnable counted(Runnable teardown) {
        teardowns.incrementAndGet();
        return () -> {
            try {
                teardown.run();
            } finally {
                teardowns.decrementAndGet();
            }
        };
    }

    public synchronized boolean settled() {
        return super.isEmpty() && teardowns.get() == 0;
    }

    @Override
    public synchronized ChunkHolder put(long key, ChunkHolder value) {
        return super.put(key, value);
    }

    @Override
    public synchronized ChunkHolder remove(long key) {
        return super.remove(key);
    }

    @Override
    public synchronized boolean remove(long key, Object value) {
        if (super.get(key) != value) {
            return false;
        }

        super.remove(key);
        return true;
    }

    @Override
    public synchronized boolean remove(Object key, Object value) {
        return key instanceof Long boxed && remove(boxed.longValue(), value);
    }

    @Override
    public synchronized boolean containsKey(long key) {
        return super.containsKey(key);
    }

    @Override
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public synchronized int size() {
        return super.size();
    }
}
