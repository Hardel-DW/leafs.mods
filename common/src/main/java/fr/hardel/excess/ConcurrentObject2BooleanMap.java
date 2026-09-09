package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.AbstractObject2BooleanMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** ConcurrentHashMap-backed Object2BooleanMap: atomic point ops, weakly consistent iteration, no nulls. */
public final class ConcurrentObject2BooleanMap<K> extends AbstractObject2BooleanMap<K> {
    private final ConcurrentHashMap<K, Boolean> map = new ConcurrentHashMap<>();

    @Override
    public boolean getBoolean(Object key) {
        Boolean value = map.get(key);
        return value == null ? defaultReturnValue() : value;
    }

    @Override
    public boolean getOrDefault(Object key, boolean defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean put(K key, boolean value) {
        Boolean previous = map.put(key, value);
        return previous == null ? defaultReturnValue() : previous;
    }

    @Override
    public boolean removeBoolean(Object key) {
        Boolean previous = map.remove(key);
        return previous == null ? defaultReturnValue() : previous;
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
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
    public @NonNull ObjectSet<K> keySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NonNull ObjectIterator<K> iterator() {
                return ObjectIterators.asObjectIterator(map.keySet().iterator());
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean contains(Object key) {
                return map.containsKey(key);
            }
        };
    }

    @Override
    public ObjectSet<Object2BooleanMap.Entry<K>> object2BooleanEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NonNull ObjectIterator<Object2BooleanMap.Entry<K>> iterator() {
                Iterator<Map.Entry<K, Boolean>> backing = map.entrySet().iterator();
                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return backing.hasNext();
                    }

                    @Override
                    public Object2BooleanMap.Entry<K> next() {
                        Map.Entry<K, Boolean> entry = backing.next();
                        return new BasicEntry<>(entry.getKey(), entry.getValue());
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }
        };
    }
}
