package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;
import it.unimi.dsi.fastutil.shorts.AbstractShort2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectFunction;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

public final class ConcurrentShort2ObjectMap<V> extends AbstractShort2ObjectMap<V> {
    private final ConcurrentHashMap<Short, V> map = new ConcurrentHashMap<>();
    private final ConcurrentValues<V> values = new ConcurrentValues<>(map);

    @Override
    public V get(short key) {
        return orDefault(map.get(key));
    }

    @Override
    public V put(short key, V value) {
        return orDefault(map.put(key, value));
    }

    @Override
    public V remove(short key) {
        return orDefault(map.remove(key));
    }

    @Override
    public V putIfAbsent(short key, V value) {
        return orDefault(map.putIfAbsent(key, value));
    }

    @Override
    public boolean remove(short key, Object value) {
        return map.remove(key, value);
    }

    @Override
    public boolean replace(short key, V oldValue, V newValue) {
        return map.replace(key, oldValue, newValue);
    }

    @Override
    public V replace(short key, V value) {
        return orDefault(map.replace(key, value));
    }

    @Override
    public V computeIfAbsent(short key, IntFunction<? extends V> mapping) {
        return orDefault(map.computeIfAbsent(key, mapping::apply));
    }

    @Override
    public V computeIfAbsent(short key, Short2ObjectFunction<? extends V> mapping) {
        return orDefault(map.computeIfAbsent(key, boxed -> mapping.get((short) boxed)));
    }

    @Override
    public V computeIfPresent(short key, BiFunction<? super Short, ? super V, ? extends V> remapping) {
        return orDefault(map.computeIfPresent(key, remapping));
    }

    @Override
    public V compute(short key, BiFunction<? super Short, ? super V, ? extends V> remapping) {
        return orDefault(map.compute(key, remapping));
    }

    @Override
    public V merge(short key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        return orDefault(map.merge(key, value, remapping));
    }

    @Override
    public void replaceAll(BiFunction<? super Short, ? super V, ? extends V> function) {
        map.replaceAll(function);
    }

    private V orDefault(V value) {
        return value == null ? defaultReturnValue() : value;
    }

    @Override
    public boolean containsKey(short key) {
        return map.containsKey(key);
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
    public ObjectSet<Short2ObjectMap.Entry<V>> short2ObjectEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NonNull ObjectIterator<Short2ObjectMap.Entry<V>> iterator() {
                Iterator<Map.Entry<Short, V>> backing = map.entrySet().iterator();
                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return backing.hasNext();
                    }

                    @Override
                    public Short2ObjectMap.Entry<V> next() {
                        Map.Entry<Short, V> entry = backing.next();
                        return new BasicEntry<>(entry.getKey(), entry.getValue());
                    }
                };
            }

            @Override
            public @NonNull ObjectSpliterator<Short2ObjectMap.Entry<V>> spliterator() {
                return ObjectSpliterators.asSpliteratorUnknownSize(iterator(), 0);
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean contains(Object object) {
                return object instanceof Map.Entry<?, ?> entry && entry.getKey() instanceof Short key && entry.getValue() != null && entry.getValue().equals(map.get(key));
            }
        };
    }
}
