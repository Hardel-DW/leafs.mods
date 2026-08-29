package fr.hardel.leafs.chunk.core;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import net.minecraft.server.level.ChunkHolder;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** The one holder table, both vanilla fields point here. The superclass stays empty, so every surface not delegated below throws rather than lie. */
public final class ConcurrentChunkTable extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {

    private final ConcurrentLong2ObjectMap<ChunkHolder> holders = new ConcurrentLong2ObjectMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();

    public ConcurrentChunkTable() {
        super(0);
    }

    /** What promoteChunkMap becomes: true when holders appeared or vanished since the last call. */
    public boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    @Override
    public ChunkHolder get(long key) {
        return holders.get(key);
    }

    @Override
    public ChunkHolder get(Object key) {
        return key instanceof Long boxed ? holders.get(boxed.longValue()) : null;
    }

    @Override
    public ChunkHolder getOrDefault(long key, ChunkHolder defaultValue) {
        ChunkHolder holder = holders.get(key);
        return holder == null ? defaultValue : holder;
    }

    @Override
    public ChunkHolder put(long key, ChunkHolder value) {
        dirty.set(true);
        return holders.put(key, value);
    }

    @Override
    public ChunkHolder putIfAbsent(long key, ChunkHolder value) {
        dirty.set(true);
        return holders.putIfAbsent(key, value);
    }

    @Override
    public ChunkHolder remove(long key) {
        dirty.set(true);
        return holders.remove(key);
    }

    @Override
    public boolean remove(long key, Object value) {
        dirty.set(true);
        return holders.remove(key, value);
    }

    @Override
    public boolean containsKey(long key) {
        return holders.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return value != null && holders.containsValue(value);
    }

    @Override
    public int size() {
        return holders.size();
    }

    @Override
    public boolean isEmpty() {
        return holders.isEmpty();
    }

    @Override
    public void clear() {
        dirty.set(true);
        holders.clear();
    }

    @Override
    public void forEach(BiConsumer<? super Long, ? super ChunkHolder> action) {
        for (Long2ObjectMap.Entry<ChunkHolder> entry : holders.long2ObjectEntrySet()) {
            action.accept(entry.getLongKey(), entry.getValue());
        }
    }

    /** The double buffer is gone, both vanilla fields must observe the same instance. */
    @Override
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> clone() {
        return this;
    }

    @Override
    public @NonNull ObjectCollection<ChunkHolder> values() {
        return holders.values();
    }

    /** The sorted key view has no meaning over an unordered backing, and nothing in the chunk system asks for it. */
    @Override
    public @NonNull LongSortedSet keySet() {
        throw new UnsupportedOperationException("The chunk table has no key order; iterate long2ObjectEntrySet or values instead");
    }

    @Override
    public Long2ObjectSortedMap.FastSortedEntrySet<ChunkHolder> long2ObjectEntrySet() {
        return new EntrySetView();
    }

    private final class EntrySetView extends AbstractObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>>
        implements Long2ObjectSortedMap.FastSortedEntrySet<ChunkHolder> {

        @Override
        public @NonNull ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> iterator() {
            ObjectIterator<Long2ObjectMap.Entry<ChunkHolder>> backing = holders.long2ObjectEntrySet().iterator();
            return new ObjectBidirectionalIterator<>() {
                @Override
                public boolean hasNext() {
                    return backing.hasNext();
                }

                @Override
                public Long2ObjectMap.Entry<ChunkHolder> next() {
                    return backing.next();
                }

                @Override
                public boolean hasPrevious() {
                    return false;
                }

                @Override
                public Long2ObjectMap.Entry<ChunkHolder> previous() {
                    throw new UnsupportedOperationException("The chunk table iterates forward only");
                }
            };
        }

        @Override
        public ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> fastIterator() {
            return iterator();
        }

        @Override
        public ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> fastIterator(Long2ObjectMap.Entry<ChunkHolder> from) {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> iterator(Long2ObjectMap.Entry<ChunkHolder> fromElement) {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public int size() {
            return holders.size();
        }

        @Override
        public boolean contains(Object object) {
            return holders.long2ObjectEntrySet().contains(object);
        }

        @Override
        public Comparator<? super Long2ObjectMap.Entry<ChunkHolder>> comparator() {
            return null;
        }

        @Override
        public @NonNull ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> subSet(Long2ObjectMap.Entry<ChunkHolder> fromElement, Long2ObjectMap.Entry<ChunkHolder> toElement) {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public @NonNull ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> headSet(Long2ObjectMap.Entry<ChunkHolder> toElement) {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public @NonNull ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> tailSet(Long2ObjectMap.Entry<ChunkHolder> fromElement) {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public Long2ObjectMap.Entry<ChunkHolder> first() {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public Long2ObjectMap.Entry<ChunkHolder> last() {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }
    }
}
