package fr.hardel.leafs.chunk.holder;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;

import java.util.List;

/** Vanilla's pendingUnloads: the holders between their last level and their teardown, claimed under one monitor from any thread. */
public final class PendingUnloads extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {
    @Override
    public synchronized ChunkHolder put(long key, ChunkHolder value) {
        return super.put(key, value);
    }

    @Override
    public synchronized ChunkHolder remove(long key) {
        return super.remove(key);
    }

    /** The primitive overload is the one vanilla's teardown resolves to. */
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

    public synchronized List<ChunkHolder> snapshot() {
        return List.copyOf(values());
    }
}
