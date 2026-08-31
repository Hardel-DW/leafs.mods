package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/** ConcurrentHashMap-backed Long2ObjectMap over spread keys: lock-free reads, atomic point ops, weakly consistent iteration, no nulls. */
public final class ConcurrentLong2ObjectMap<V> extends AbstractLong2ObjectMap<V> {
    private final ConcurrentHashMap<Long, V> map = new ConcurrentHashMap<>();

    @Override
    public V get(long key) {
        V value = map.get(LongSpread.mix(key));
        return value == null ? defaultReturnValue() : value;
    }

    @Override
    public V put(long key, V value) {
        V previous = map.put(LongSpread.mix(key), value);
        return previous == null ? defaultReturnValue() : previous;
    }

    /** The value in place, or null when the key was free and the value stored. */
    public V putIfAbsent(long key, V value) {
        return map.putIfAbsent(LongSpread.mix(key), value);
    }

    @Override
    public V remove(long key) {
        V previous = map.remove(LongSpread.mix(key));
        return previous == null ? defaultReturnValue() : previous;
    }

    public boolean remove(long key, Object value) {
        return map.remove(LongSpread.mix(key), value);
    }

    @Override
    public V computeIfAbsent(long key, LongFunction<? extends V> mappingFunction) {
        return map.computeIfAbsent(LongSpread.mix(key), _ -> mappingFunction.apply(key));
    }

    @Override
    public V computeIfAbsent(long key, Long2ObjectFunction<? extends V> mappingFunction) {
        V existing = get(key);
        if (existing != null) {
            return existing;
        }

        if (!mappingFunction.containsKey(key)) {
            return defaultReturnValue();
        }

        return map.computeIfAbsent(LongSpread.mix(key), _ -> mappingFunction.get(key));
    }

    /** Atomic read-modify-write of one key; a null result removes it. */
    @Override
    public V compute(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return map.compute(LongSpread.mix(key), (_, value) -> remappingFunction.apply(key, value));
    }

    @Override
    public boolean containsKey(long key) {
        return map.containsKey(LongSpread.mix(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public @NonNull ObjectCollection<V> values() {
        return new AbstractObjectCollection<>() {
            @Override
            public @NonNull ObjectIterator<V> iterator() {
                return ObjectIterators.asObjectIterator(map.values().iterator());
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean contains(Object value) {
                return map.containsValue(value);
            }

            @Override
            public void clear() {
                map.clear();
            }
        };
    }

    @Override
    public ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public ObjectIterator<Long2ObjectMap.Entry<V>> iterator() {
                Iterator<Map.Entry<Long, V>> backing = map.entrySet().iterator();
                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return backing.hasNext();
                    }

                    @Override
                    public Long2ObjectMap.Entry<V> next() {
                        Map.Entry<Long, V> entry = backing.next();
                        return new BasicEntry<>(LongSpread.unmix(entry.getKey()), entry.getValue());
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean contains(Object object) {
                return object instanceof Map.Entry<?, ?> entry && entry.getKey() instanceof Long key && entry.getValue() != null && entry.getValue().equals(get(key.longValue()));
            }
        };
    }
}
