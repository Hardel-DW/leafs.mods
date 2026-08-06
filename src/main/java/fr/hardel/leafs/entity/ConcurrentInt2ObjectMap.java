package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** ConcurrentHashMap-backed Int2ObjectMap: atomic point ops, weakly consistent iteration, no nulls. */
public final class ConcurrentInt2ObjectMap<V> extends AbstractInt2ObjectMap<V> {
    private final ConcurrentHashMap<Integer, V> map = new ConcurrentHashMap<>();

    @Override
    public V get(int key) {
        V value = map.get(key);
        return value == null ? defaultReturnValue() : value;
    }

    @Override
    public V put(int key, V value) {
        V previous = map.put(key, value);
        return previous == null ? defaultReturnValue() : previous;
    }

    @Override
    public V remove(int key) {
        V previous = map.remove(key);
        return previous == null ? defaultReturnValue() : previous;
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
    public ObjectCollection<V> values() {
        return new AbstractObjectCollection<>() {
            @Override
            public ObjectIterator<V> iterator() {
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
    public ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public ObjectIterator<Int2ObjectMap.Entry<V>> iterator() {
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
            public int size() {
                return map.size();
            }

            @Override
            public boolean contains(Object object) {
                return object instanceof Map.Entry<?, ?> entry
                        && entry.getKey() instanceof Integer key
                        && entry.getValue() != null
                        && entry.getValue().equals(map.get(key));
            }
        };
    }
}
