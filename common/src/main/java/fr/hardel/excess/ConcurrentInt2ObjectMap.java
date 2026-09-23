package fr.hardel.excess;

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

public final class ConcurrentInt2ObjectMap<V> extends AbstractInt2ObjectMap<V> {
    private final ConcurrentHashMap<Integer, V> map = new ConcurrentHashMap<>();

    @Override
    public V get(int key) {
        return orDefault(map.get(key));
    }

    @Override
    public V put(int key, V value) {
        return orDefault(map.put(key, value));
    }

    @Override
    public V remove(int key) {
        return orDefault(map.remove(key));
    }

    @Override
    public V putIfAbsent(int key, V value) {
        return orDefault(map.putIfAbsent(key, value));
    }

    @Override
    public boolean remove(int key, Object value) {
        return map.remove(key, value);
    }

    @Override
    public boolean replace(int key, V oldValue, V newValue) {
        return map.replace(key, oldValue, newValue);
    }

    @Override
    public V replace(int key, V value) {
        return orDefault(map.replace(key, value));
    }

    @Override
    public V computeIfAbsent(int key, IntFunction<? extends V> mapping) {
        return orDefault(map.computeIfAbsent(key, mapping::apply));
    }

    @Override
    public V computeIfAbsent(int key, Int2ObjectFunction<? extends V> mapping) {
        return orDefault(map.computeIfAbsent(key, mapping::get));
    }

    @Override
    public V computeIfPresent(int key, BiFunction<? super Integer, ? super V, ? extends V> remapping) {
        return orDefault(map.computeIfPresent(key, remapping));
    }

    @Override
    public V compute(int key, BiFunction<? super Integer, ? super V, ? extends V> remapping) {
        return orDefault(map.compute(key, remapping));
    }

    @Override
    public V merge(int key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        return orDefault(map.merge(key, value, remapping));
    }

    private V orDefault(V value) {
        return value == null ? defaultReturnValue() : value;
    }

    @Override
    public boolean containsKey(int key) {
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
        return new AbstractObjectCollection<>() {
            @Override
            public @NonNull ObjectIterator<V> iterator() {
                return ObjectIterators.asObjectIterator(map.values().iterator());
            }

            @Override
            public @NonNull ObjectSpliterator<V> spliterator() {
                return ObjectSpliterators.asSpliteratorUnknownSize(iterator(), 0);
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
    public ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NonNull ObjectIterator<Int2ObjectMap.Entry<V>> iterator() {
                Iterator<Map.Entry<Integer, V>> backing = map.entrySet().iterator();
                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return backing.hasNext();
                    }

                    @Override
                    public Int2ObjectMap.Entry<V> next() {
                        Map.Entry<Integer, V> entry = backing.next();
                        return new BasicEntry<>(entry.getKey(), entry.getValue());
                    }
                };
            }

            @Override
            public @NonNull ObjectSpliterator<Int2ObjectMap.Entry<V>> spliterator() {
                return ObjectSpliterators.asSpliteratorUnknownSize(iterator(), 0);
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean contains(Object object) {
                return object instanceof Map.Entry<?, ?> entry && entry.getKey() instanceof Integer key && entry.getValue() != null && entry.getValue().equals(map.get(key));
            }
        };
    }
}
