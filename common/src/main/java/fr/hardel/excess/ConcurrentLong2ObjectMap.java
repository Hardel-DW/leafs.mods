package fr.hardel.excess;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

public final class ConcurrentLong2ObjectMap<V> extends AbstractLong2ObjectMap<V> {
    private final ConcurrentHashMap<Long, V> map = new ConcurrentHashMap<>();
    private final ConcurrentValues<V> values = new ConcurrentValues<>(map);

    @Override
    public V get(long key) {
        V value = map.get(HashCommon.mix(key));
        return value == null ? defaultReturnValue() : value;
    }

    @Override
    public V put(long key, V value) {
        V previous = map.put(HashCommon.mix(key), value);
        return previous == null ? defaultReturnValue() : previous;
    }

    public V putIfAbsent(long key, V value) {
        return map.putIfAbsent(HashCommon.mix(key), value);
    }

    @Override
    public V remove(long key) {
        V previous = map.remove(HashCommon.mix(key));
        return previous == null ? defaultReturnValue() : previous;
    }

    public boolean remove(long key, Object value) {
        return map.remove(HashCommon.mix(key), value);
    }

    @Override
    public V computeIfAbsent(long key, LongFunction<? extends V> mappingFunction) {
        return map.computeIfAbsent(HashCommon.mix(key), _ -> mappingFunction.apply(key));
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

        return map.computeIfAbsent(HashCommon.mix(key), _ -> mappingFunction.get(key));
    }

    @Override
    public V compute(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return map.compute(HashCommon.mix(key), (_, value) -> remappingFunction.apply(key, value));
    }

    @Override
    public boolean containsKey(long key) {
        return map.containsKey(HashCommon.mix(key));
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
        return values;
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
                        return new BasicEntry<>(HashCommon.invMix(entry.getKey()), entry.getValue());
                    }
                };
            }

            @Override
            public @NonNull ObjectSpliterator<Long2ObjectMap.Entry<V>> spliterator() {
                return ObjectSpliterators.asSpliteratorUnknownSize(iterator(), 0);
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
