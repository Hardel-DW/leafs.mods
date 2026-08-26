package fr.hardel.leafs.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;

/** ChunkMap's pendingUnloads made atomic: a drain revives while the owner's teardown claims, the conditional remove must not tear. */
public final class PendingUnloadClaims extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {

    @Override
    public synchronized ChunkHolder put(long key, ChunkHolder value) {
        return super.put(key, value);
    }

    @Override
    public synchronized ChunkHolder remove(long key) {
        return super.remove(key);
    }

    /** The primitive overload is the one vanilla's teardown resolves to; both must claim under the monitor. */
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
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }
}
