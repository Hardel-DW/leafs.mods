package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.AbstractObject2BooleanMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2BooleanFunction;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ConcurrentObject2BooleanMap<K> extends AbstractObject2BooleanMap<K> {
    private final ConcurrentHashMap<K, Boolean> map = new ConcurrentHashMap<>();

    @Override
    public boolean getBoolean(Object key) {
        return orDefault(map.get(key));
    }

    @Override
    public boolean getOrDefault(Object key, boolean defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean put(K key, boolean value) {
        return orDefault(map.put(key, value));
    }

    @Override
    public boolean removeBoolean(Object key) {
        return orDefault(map.remove(key));
    }

    @Override
    public boolean putIfAbsent(K key, boolean value) {
        return orDefault(map.putIfAbsent(key, value));
    }

    @Override
    public boolean remove(Object key, boolean value) {
        return map.remove(key, value);
    }

    @Override
    public boolean replace(K key, boolean oldValue, boolean newValue) {
        return map.replace(key, oldValue, newValue);
    }

    @Override
    public boolean replace(K key, boolean value) {
        return orDefault(map.replace(key, value));
    }

    @Override
    public boolean computeIfAbsent(K key, Predicate<? super K> mapping) {
        return orDefault(map.computeIfAbsent(key, mapping::test));
    }

    @Override
    public boolean computeIfAbsent(K key, Object2BooleanFunction<? super K> mapping) {
        return orDefault(map.computeIfAbsent(key, mapping::getBoolean));
    }

    @Override
    public boolean computeBooleanIfPresent(K key, BiFunction<? super K, ? super Boolean, ? extends Boolean> remapping) {
        return orDefault(map.computeIfPresent(key, remapping));
    }

    @Override
    public boolean computeBoolean(K key, BiFunction<? super K, ? super Boolean, ? extends Boolean> remapping) {
        return orDefault(map.compute(key, remapping));
    }

    @Override
    public boolean merge(K key, boolean value, BiFunction<? super Boolean, ? super Boolean, ? extends Boolean> remapping) {
        return orDefault(map.merge(key, value, remapping));
    }

    @Override
    public Boolean putIfAbsent(K key, Boolean value) {
        return map.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return map.remove(key, value);
    }

    @Override
    public boolean replace(K key, Boolean oldValue, Boolean newValue) {
        return map.replace(key, oldValue, newValue);
    }

    @Override
    public Boolean replace(K key, Boolean value) {
        return map.replace(key, value);
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super Boolean, ? extends Boolean> function) {
        map.replaceAll(function);
    }

    @Override
    public Boolean computeIfAbsent(K key, Function<? super K, ? extends Boolean> mapping) {
        return map.computeIfAbsent(key, mapping);
    }

    @Override
    public Boolean computeIfPresent(K key, BiFunction<? super K, ? super Boolean, ? extends Boolean> remapping) {
        return map.computeIfPresent(key, remapping);
    }

    @Override
    public Boolean compute(K key, BiFunction<? super K, ? super Boolean, ? extends Boolean> remapping) {
        return map.compute(key, remapping);
    }

    @Override
    public Boolean merge(K key, Boolean value, BiFunction<? super Boolean, ? super Boolean, ? extends Boolean> remapping) {
        return map.merge(key, value, remapping);
    }

    private boolean orDefault(Boolean value) {
        return value == null ? defaultReturnValue() : value;
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
