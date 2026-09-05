package fr.hardel.leafs.chunk.pool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** The placed tasks still waiting in the pool, under the chunk they write and the centre they serve. */
final class PlacedTasks {
    private final ConcurrentHashMap<Long, Set<ChunkTask>> byChunk = new ConcurrentHashMap<>();

    void add(ChunkTask task) {
        ChunkTask.Place place = task.place();
        if (place == null) {
            return;
        }

        index(place.chunkKey(), task);
        if (place.centerKey() != place.chunkKey()) {
            index(place.centerKey(), task);
        }
    }

    void remove(ChunkTask task) {
        ChunkTask.Place place = task.place();
        if (place == null) {
            return;
        }

        unindex(place.chunkKey(), task);
        if (place.centerKey() != place.chunkKey()) {
            unindex(place.centerKey(), task);
        }
    }

    void forEachAt(long key, Consumer<ChunkTask> action) {
        Set<ChunkTask> at = byChunk.get(key);
        if (at != null) {
            at.forEach(action);
        }
    }

    int countAt(long key) {
        Set<ChunkTask> at = byChunk.get(key);
        return at == null ? 0 : at.size();
    }

    private void index(long key, ChunkTask task) {
        byChunk.compute(key, (_, at) -> {
            Set<ChunkTask> target = at == null ? ConcurrentHashMap.newKeySet() : at;
            target.add(task);
            return target;
        });
    }

    private void unindex(long key, ChunkTask task) {
        byChunk.computeIfPresent(key, (_, at) -> {
            at.remove(task);
            return at.isEmpty() ? null : at;
        });
    }
}
