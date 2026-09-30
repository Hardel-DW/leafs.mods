package fr.hardel.leafs.network;

import fr.hardel.excess.ConcurrentLongSet;
import org.jspecify.annotations.Nullable;

import java.util.function.LongFunction;

public final class AwaitedChunks {
    private final ConcurrentLongSet awaited = new ConcurrentLongSet();

    public <T> @Nullable T entered(long chunkKey, LongFunction<@Nullable T> sendable) {
        awaited.add(chunkKey);
        T chunk = sendable.apply(chunkKey);
        return chunk != null && awaited.remove(chunkKey) ? chunk : null;
    }

    public boolean awaits(long chunkKey) {
        return awaited.contains(chunkKey);
    }

    public boolean take(long chunkKey) {
        return awaited.remove(chunkKey);
    }
}
