package fr.hardel.leafs.entity;

import java.util.function.LongFunction;

/** Routes entity lifecycle to the owning region's tick list; a cross-region move lands at the target's next pass. */
public final class RegionEntityLists<E> {
    private final LongFunction<RegionEntityTickList<E>> listAt;

    public RegionEntityLists(LongFunction<RegionEntityTickList<E>> listAt) {
        this.listAt = listAt;
    }

    public void entityAdded(int id, E entity, long chunkKey) {
        listAt.apply(chunkKey).add(id, entity, chunkKey);
    }

    public void entityRemoved(int id, long chunkKey) {
        listAt.apply(chunkKey).remove(id);
    }

    public void entityMoved(int id, E entity, long oldChunkKey, long newChunkKey) {
        RegionEntityTickList<E> from = listAt.apply(oldChunkKey);
        RegionEntityTickList<E> to = listAt.apply(newChunkKey);
        if (from == to) {
            from.move(id, newChunkKey);
        } else {
            from.remove(id);
            to.queueAdd(id, entity, newChunkKey);
        }
    }
}
