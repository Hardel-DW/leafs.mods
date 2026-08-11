package fr.hardel.leafs.chunk.core;

import it.unimi.dsi.fastutil.longs.AbstractLongSortedSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import java.util.Comparator;
import net.minecraft.server.level.ChunkHolder;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * The one chunk holder table of a level, replacing vanilla's updating/visible double buffer: both
 * fields point at this instance, every thread reads it lock-free, and the promotion step reduces to
 * consuming the dirty flag. Extends the vanilla field type so the swap stays invisible to mods; the
 * superclass storage stays empty, every operation delegates to the concurrent backing.
 */
public final class ConcurrentChunkTable extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {

    private final ConcurrentHashMap<Long, ChunkHolder> holders = new ConcurrentHashMap<>(1024);
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
        return key instanceof Long boxed ? holders.get(boxed) : null;
    }

    @Override
    public ChunkHolder getOrDefault(long key, ChunkHolder defaultValue) {
        return holders.getOrDefault(key, defaultValue);
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
        holders.forEach(action);
    }

    /** The double buffer is gone, both vanilla fields must observe the same instance. */
    @Override
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> clone() {
        return this;
    }

    @Override
    public ObjectCollection<ChunkHolder> values() {
        return new AbstractObjectCollection<>() {
            @Override
            public ObjectIterator<ChunkHolder> iterator() {
                return ObjectIterators.asObjectIterator(holders.values().iterator());
            }

            @Override
            public int size() {
                return holders.size();
            }

            @Override
            public boolean contains(Object value) {
                return value != null && holders.containsValue(value);
            }
        };
    }

    /** Iteration order is undefined and the sorted-range operations are unsupported; no chunk system caller uses either. */
    @Override
    public LongSortedSet keySet() {
        return new AbstractLongSortedSet() {
            @Override
            public LongBidirectionalIterator iterator() {
                Iterator<Long> backing = holders.keySet().iterator();
                return new LongBidirectionalIterator() {
                    @Override
                    public boolean hasNext() {
                        return backing.hasNext();
                    }

                    @Override
                    public long nextLong() {
                        return backing.next();
                    }

                    @Override
                    public boolean hasPrevious() {
                        return false;
                    }

                    @Override
                    public long previousLong() {
                        throw new UnsupportedOperationException("The chunk table iterates forward only");
                    }
                };
            }

            @Override
            public LongBidirectionalIterator iterator(long fromElement) {
                throw new UnsupportedOperationException("The chunk table has no key order");
            }

            @Override
            public int size() {
                return holders.size();
            }

            @Override
            public boolean contains(long key) {
                return holders.containsKey(key);
            }

            @Override
            public LongComparator comparator() {
                return null;
            }

            @Override
            public LongSortedSet subSet(long fromElement, long toElement) {
                throw new UnsupportedOperationException("The chunk table has no key order");
            }

            @Override
            public LongSortedSet headSet(long toElement) {
                throw new UnsupportedOperationException("The chunk table has no key order");
            }

            @Override
            public LongSortedSet tailSet(long fromElement) {
                throw new UnsupportedOperationException("The chunk table has no key order");
            }

            @Override
            public long firstLong() {
                throw new UnsupportedOperationException("The chunk table has no key order");
            }

            @Override
            public long lastLong() {
                throw new UnsupportedOperationException("The chunk table has no key order");
            }
        };
    }

    @Override
    public Long2ObjectSortedMap.FastSortedEntrySet<ChunkHolder> long2ObjectEntrySet() {
        return new EntrySetView();
    }

    private final class EntrySetView extends AbstractObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>>
        implements Long2ObjectSortedMap.FastSortedEntrySet<ChunkHolder> {

        @Override
        public ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> iterator() {
            Iterator<Map.Entry<Long, ChunkHolder>> backing = holders.entrySet().iterator();
            return new ObjectBidirectionalIterator<>() {
                @Override
                public boolean hasNext() {
                    return backing.hasNext();
                }

                @Override
                public Long2ObjectMap.Entry<ChunkHolder> next() {
                    Map.Entry<Long, ChunkHolder> entry = backing.next();
                    return new BasicEntry<>(entry.getKey().longValue(), entry.getValue());
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
            return object instanceof Map.Entry<?, ?> entry
                && entry.getKey() instanceof Long key
                && entry.getValue() != null
                && entry.getValue().equals(holders.get(key));
        }

        @Override
        public Comparator<? super Long2ObjectMap.Entry<ChunkHolder>> comparator() {
            return null;
        }

        @Override
        public ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> subSet(Long2ObjectMap.Entry<ChunkHolder> fromElement, Long2ObjectMap.Entry<ChunkHolder> toElement) {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> headSet(Long2ObjectMap.Entry<ChunkHolder> toElement) {
            throw new UnsupportedOperationException("The chunk table has no entry order");
        }

        @Override
        public ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> tailSet(Long2ObjectMap.Entry<ChunkHolder> fromElement) {
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
