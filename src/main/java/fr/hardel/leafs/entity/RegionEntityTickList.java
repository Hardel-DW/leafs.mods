package fr.hardel.leafs.entity;

import fr.hardel.leafs.region.CoordinateKey;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Vanilla EntityTickList semantics per region (in-iteration add/remove safe), plus what re-homing
 * needs: chunk keys for split re-bucketing, and cross-region arrivals buffered to the next pass so
 * an entity crossing regions mid-tick is never ticked twice in one pass.
 */
public final class RegionEntityTickList<E> {
    private Int2ObjectMap<E> active = new Int2ObjectLinkedOpenHashMap<>();
    private Int2ObjectMap<E> passive = new Int2ObjectLinkedOpenHashMap<>();
    private Int2ObjectMap<E> iterated;
    private final Int2LongMap chunkKeys = new Int2LongOpenHashMap();
    private final Int2ObjectMap<E> pendingAdds = new Int2ObjectLinkedOpenHashMap<>();
    private final Int2LongMap pendingChunkKeys = new Int2LongOpenHashMap();

    public void add(int id, E entity, long chunkKey) {
        ensureActiveIsNotIterated();
        active.put(id, entity);
        chunkKeys.put(id, chunkKey);
    }

    public void queueAdd(int id, E entity, long chunkKey) {
        pendingAdds.put(id, entity);
        pendingChunkKeys.put(id, chunkKey);
    }

    public void remove(int id) {
        ensureActiveIsNotIterated();
        active.remove(id);
        chunkKeys.remove(id);
        pendingAdds.remove(id);
        pendingChunkKeys.remove(id);
    }

    public boolean contains(int id) {
        return active.containsKey(id) || pendingAdds.containsKey(id);
    }

    public void move(int id, long chunkKey) {
        if (active.containsKey(id)) {
            chunkKeys.put(id, chunkKey);
        } else if (pendingAdds.containsKey(id)) {
            pendingChunkKeys.put(id, chunkKey);
        }
    }

    public void beginTick() {
        ensureActiveIsNotIterated();
        active.putAll(pendingAdds);
        chunkKeys.putAll(pendingChunkKeys);
        pendingAdds.clear();
        pendingChunkKeys.clear();
    }

    public void forEach(Consumer<E> output) {
        if (iterated != null) {
            throw new UnsupportedOperationException("Only one concurrent iteration supported");
        }

        iterated = active;
        try {
            for (E entity : active.values()) {
                output.accept(entity);
            }
        } finally {
            iterated = null;
        }
    }

    public int size() {
        return active.size();
    }

    public void mergeInto(RegionEntityTickList<E> target) {
        target.ensureActiveIsNotIterated();
        target.active.putAll(active);
        target.chunkKeys.putAll(chunkKeys);
        target.pendingAdds.putAll(pendingAdds);
        target.pendingChunkKeys.putAll(pendingChunkKeys);
        active.clear();
        chunkKeys.clear();
        pendingAdds.clear();
        pendingChunkKeys.clear();
    }

    /** Entities whose section died with the split are dropped, like the payload's other position-keyed state. */
    public void splitInto(int sectionShift, LongFunction<RegionEntityTickList<E>> targetBySection) {
        for (int id : active.keySet().toIntArray()) {
            RegionEntityTickList<E> target = targetBySection.apply(sectionOf(chunkKeys.get(id), sectionShift));
            if (target != null) {
                target.add(id, active.get(id), chunkKeys.get(id));
            }
        }
        
        for (int id : pendingAdds.keySet().toIntArray()) {
            RegionEntityTickList<E> target = targetBySection.apply(sectionOf(pendingChunkKeys.get(id), sectionShift));
            if (target != null) {
                target.queueAdd(id, pendingAdds.get(id), pendingChunkKeys.get(id));
            }
        }

        active.clear();
        chunkKeys.clear();
        pendingAdds.clear();
        pendingChunkKeys.clear();
    }

    private static long sectionOf(long chunkKey, int sectionShift) {
        return CoordinateKey.pack(CoordinateKey.x(chunkKey) >> sectionShift, CoordinateKey.z(chunkKey) >> sectionShift);
    }

    private void ensureActiveIsNotIterated() {
        if (iterated == active) {
            passive.clear();
            passive.putAll(active);
            Int2ObjectMap<E> current = active;
            active = passive;
            passive = current;
        }
    }
}
