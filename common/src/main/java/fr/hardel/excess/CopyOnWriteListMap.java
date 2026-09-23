package fr.hardel.excess;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class CopyOnWriteListMap<K, T> extends ConcurrentHashMap<K, List<T>> {

    @Override
    public List<T> put(K key, List<T> value) {
        return super.put(key, copyOnWrite(value));
    }

    @Override
    public List<T> putIfAbsent(K key, List<T> value) {
        return super.putIfAbsent(key, copyOnWrite(value));
    }

    @Override
    public void putAll(Map<? extends K, ? extends List<T>> entries) {
        entries.forEach(this::put);
    }

    @Override
    public List<T> replace(K key, List<T> value) {
        return super.replace(key, copyOnWrite(value));
    }

    @Override
    public boolean replace(K key, List<T> oldValue, List<T> newValue) {
        return super.replace(key, oldValue, copyOnWrite(newValue));
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super List<T>, ? extends List<T>> function) {
        super.replaceAll((key, value) -> copyOnWrite(function.apply(key, value)));
    }

    @Override
    public List<T> computeIfAbsent(K key, Function<? super K, ? extends List<T>> mapping) {
        return super.computeIfAbsent(key, k -> copyOnWrite(mapping.apply(k)));
    }

    @Override
    public List<T> computeIfPresent(K key, BiFunction<? super K, ? super List<T>, ? extends List<T>> remapping) {
        return super.computeIfPresent(key, (k, value) -> copyOnWrite(remapping.apply(k, value)));
    }

    @Override
    public List<T> compute(K key, BiFunction<? super K, ? super List<T>, ? extends List<T>> remapping) {
        return super.compute(key, (k, value) -> copyOnWrite(remapping.apply(k, value)));
    }

    @Override
    public List<T> merge(K key, List<T> value, BiFunction<? super List<T>, ? super List<T>, ? extends List<T>> remapping) {
        return super.merge(key, copyOnWrite(value), (current, _) -> copyOnWrite(remapping.apply(current, value)));
    }

    @Override
    public KeySetView<K, List<T>> keySet(List<T> mappedValue) {
        return super.keySet(copyOnWrite(mappedValue));
    }

    private static <T> List<T> copyOnWrite(List<T> list) {
        return list == null || list instanceof CopyOnWriteArrayList<T> ? list : new CopyOnWriteArrayList<>(list);
    }
}
