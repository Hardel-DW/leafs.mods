package fr.hardel.leafs.chunk.holder;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.region.CoordinateKey;
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
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The one holder table behind both vanilla fields, plus the same holders by region section. The empty superclass makes any surface not delegated here throw. */
public final class HolderTable extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {
    private final ConcurrentLong2ObjectMap<ChunkHolder> holders = new ConcurrentLong2ObjectMap<>();
    private final ConcurrentLong2ObjectMap<AtomicReferenceArray<ChunkHolder>> sections = new ConcurrentLong2ObjectMap<>();
    private final int sectionShift;

    public HolderTable(int sectionShift) {
        super(0);
        this.sectionShift = sectionShift;
    }

    /** A region photographs its chunks section by section. */
    public void forEachHolderIn(long sectionKey, Consumer<ChunkHolder> action) {
        AtomicReferenceArray<ChunkHolder> slots = sections.get(sectionKey);
        if (slots == null) {
            return;
        }

        for (int slot = 0; slot < slots.length(); slot++) {
            ChunkHolder holder = slots.get(slot);
            if (holder != null) {
                action.accept(holder);
            }
        }
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
        index(key, value);
        return holders.put(key, value);
    }

    @Override
    public ChunkHolder remove(long key) {
        ChunkHolder removed = holders.remove(key);
        if (removed != null) {
            unindex(key);
        }

        return removed;
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
        holders.clear();
        sections.clear();
    }

    @Override
    public void forEach(BiConsumer<? super Long, ? super ChunkHolder> action) {
        for (Long2ObjectMap.Entry<ChunkHolder> entry : holders.long2ObjectEntrySet()) {
            action.accept(entry.getLongKey(), entry.getValue());
        }
    }

    /** One atomic compute per key, so a birth and a death on the same section never lose each other. */
    private void index(long key, ChunkHolder holder) {
        sections.compute(sectionOf(key), (_, slots) -> {
            AtomicReferenceArray<ChunkHolder> target = slots == null ? new AtomicReferenceArray<>(1 << (2 * sectionShift)) : slots;
            target.set(slotOf(key), holder);
            return target;
        });
    }

    private void unindex(long key) {
        sections.compute(sectionOf(key), (_, slots) -> {
            slots.set(slotOf(key), null);
            return holdsAny(slots) ? slots : null;
        });
    }

    private static boolean holdsAny(AtomicReferenceArray<ChunkHolder> slots) {
        for (int slot = 0; slot < slots.length(); slot++) {
            if (slots.get(slot) != null) {
                return true;
            }
        }

        return false;
    }

    private long sectionOf(long key) {
        return CoordinateKey.pack(ChunkPos.getX(key) >> sectionShift, ChunkPos.getZ(key) >> sectionShift);
    }

    private int slotOf(long key) {
        return CoordinateKey.index(ChunkPos.getX(key), ChunkPos.getZ(key), sectionShift);
    }

    /** Both vanilla fields observe the same instance. */
    @Override
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> clone() {
        return this;
    }

    @Override
    public @NonNull ObjectCollection<ChunkHolder> values() {
        return holders.values();
    }

    @Override
    public @NonNull LongSortedSet keySet() {
        throw new UnsupportedOperationException("The holder table has no key order; iterate long2ObjectEntrySet or values instead");
    }

    @Override
    public Long2ObjectSortedMap.FastSortedEntrySet<ChunkHolder> long2ObjectEntrySet() {
        return new EntrySetView();
    }

    private final class EntrySetView extends AbstractObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> implements Long2ObjectSortedMap.FastSortedEntrySet<ChunkHolder> {
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
                    throw new UnsupportedOperationException("The holder table iterates forward only");
                }
            };
        }

        @Override
        public ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> fastIterator() {
            return iterator();
        }

        @Override
        public ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> fastIterator(Long2ObjectMap.Entry<ChunkHolder> from) {
            throw unordered();
        }

        @Override
        public ObjectBidirectionalIterator<Long2ObjectMap.Entry<ChunkHolder>> iterator(Long2ObjectMap.Entry<ChunkHolder> fromElement) {
            throw unordered();
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
            throw unordered();
        }

        @Override
        public @NonNull ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> headSet(Long2ObjectMap.Entry<ChunkHolder> toElement) {
            throw unordered();
        }

        @Override
        public @NonNull ObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> tailSet(Long2ObjectMap.Entry<ChunkHolder> fromElement) {
            throw unordered();
        }

        @Override
        public Long2ObjectMap.Entry<ChunkHolder> first() {
            throw unordered();
        }

        @Override
        public Long2ObjectMap.Entry<ChunkHolder> last() {
            throw unordered();
        }

        private static UnsupportedOperationException unordered() {
            return new UnsupportedOperationException("The holder table has no entry order");
        }
    }
}
