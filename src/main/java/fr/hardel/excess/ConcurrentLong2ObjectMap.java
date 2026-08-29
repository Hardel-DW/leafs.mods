package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/** A Long2ObjectMap of 64 striped fastutil maps, one monitor each: point ops never box the key, iteration walks a snapshot, no nulls. */
public final class ConcurrentLong2ObjectMap<V> extends AbstractLong2ObjectMap<V> {
    private static final int STRIPE_SHIFT = 6;
    private static final int STRIPES = 1 << STRIPE_SHIFT;
    private static final long MIX = 0x9E3779B97F4A7C15L;

    private final List<Long2ObjectOpenHashMap<V>> stripes = new ArrayList<>(STRIPES);

    public ConcurrentLong2ObjectMap() {
        for (int index = 0; index < STRIPES; index++) {
            stripes.add(new Long2ObjectOpenHashMap<>());
        }
    }

    private Long2ObjectOpenHashMap<V> stripe(long key) {
        return stripes.get((int) ((key * MIX) >>> (64 - STRIPE_SHIFT)));
    }

    @Override
    public V get(long key) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            V value = stripe.get(key);
            return value == null ? defaultReturnValue() : value;
        }
    }

    @Override
    public V put(long key, V value) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            V previous = stripe.put(key, value);
            return previous == null ? defaultReturnValue() : previous;
        }
    }

    /** The value in place, or the one stored when there was none; null when the key was free. */
    public V putIfAbsent(long key, V value) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            return stripe.putIfAbsent(key, value);
        }
    }

    @Override
    public V remove(long key) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            V previous = stripe.remove(key);
            return previous == null ? defaultReturnValue() : previous;
        }
    }

    public boolean remove(long key, Object value) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            return stripe.remove(key, value);
        }
    }

    @Override
    public V computeIfAbsent(long key, LongFunction<? extends V> mappingFunction) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            return stripe.computeIfAbsent(key, mappingFunction);
        }
    }

    @Override
    public V computeIfAbsent(long key, Long2ObjectFunction<? extends V> mappingFunction) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            V existing = stripe.get(key);
            if (existing != null) {
                return existing;
            }

            if (!mappingFunction.containsKey(key)) {
                return defaultReturnValue();
            }

            V computed = mappingFunction.get(key);
            stripe.put(key, computed);
            return computed;
        }
    }

    @Override
    public boolean containsKey(long key) {
        Long2ObjectOpenHashMap<V> stripe = stripe(key);
        synchronized (stripe) {
            return stripe.containsKey(key);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        for (Long2ObjectOpenHashMap<V> stripe : stripes) {
            synchronized (stripe) {
                if (stripe.containsValue(value)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public int size() {
        int size = 0;
        for (Long2ObjectOpenHashMap<V> stripe : stripes) {
            synchronized (stripe) {
                size += stripe.size();
            }
        }

        return size;
    }

    @Override
    public boolean isEmpty() {
        for (Long2ObjectOpenHashMap<V> stripe : stripes) {
            synchronized (stripe) {
                if (!stripe.isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void clear() {
        for (Long2ObjectOpenHashMap<V> stripe : stripes) {
            synchronized (stripe) {
                stripe.clear();
            }
        }
    }

    /** The entries of the moment, stripe by stripe; a writer racing the walk lands in a later snapshot. */
    private ObjectArrayList<Long2ObjectMap.Entry<V>> snapshot() {
        ObjectArrayList<Long2ObjectMap.Entry<V>> entries = new ObjectArrayList<>();
        for (Long2ObjectOpenHashMap<V> stripe : stripes) {
            synchronized (stripe) {
                for (Long2ObjectMap.Entry<V> entry : stripe.long2ObjectEntrySet()) {
                    entries.add(new BasicEntry<>(entry.getLongKey(), entry.getValue()));
                }
            }
        }

        return entries;
    }

    @Override
    public @NonNull ObjectCollection<V> values() {
        return new AbstractObjectCollection<>() {
            @Override
            public @NonNull ObjectIterator<V> iterator() {
                ObjectIterator<Long2ObjectMap.Entry<V>> entries = snapshot().iterator();
                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return entries.hasNext();
                    }

                    @Override
                    public V next() {
                        return entries.next().getValue();
                    }
                };
            }

            @Override
            public int size() {
                return ConcurrentLong2ObjectMap.this.size();
            }

            @Override
            public boolean contains(Object value) {
                return containsValue(value);
            }

            @Override
            public void clear() {
                ConcurrentLong2ObjectMap.this.clear();
            }
        };
    }

    @Override
    public ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public ObjectIterator<Long2ObjectMap.Entry<V>> iterator() {
                return snapshot().iterator();
            }

            @Override
            public int size() {
                return ConcurrentLong2ObjectMap.this.size();
            }

            @Override
            public boolean contains(Object object) {
                return object instanceof Map.Entry<?, ?> entry && entry.getKey() instanceof Long key && entry.getValue() != null && entry.getValue().equals(get(key.longValue()));
            }
        };
    }
}
