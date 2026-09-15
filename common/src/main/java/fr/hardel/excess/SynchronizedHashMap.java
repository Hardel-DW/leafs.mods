package fr.hardel.excess;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A HashMap other threads may write while one iterates: writes take the map's lock, the views are unmodifiable snapshots, and the functions of
 * compute and merge run outside the lock, published only if the entry did not move meanwhile. Fits a field declared HashMap or Map.
 */
public final class SynchronizedHashMap<K, V> extends HashMap<K, V> {

    @Override
    public synchronized V get(Object key) {
        return super.get(key);
    }

    @Override
    public synchronized V getOrDefault(Object key, V fallback) {
        return super.getOrDefault(key, fallback);
    }

    @Override
    public synchronized V put(K key, V value) {
        return super.put(key, value);
    }

    @Override
    public synchronized V putIfAbsent(K key, V value) {
        return super.putIfAbsent(key, value);
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> entries) {
        super.putAll(entries);
    }

    @Override
    public synchronized V remove(Object key) {
        return super.remove(key);
    }

    @Override
    public synchronized boolean remove(Object key, Object value) {
        return super.remove(key, value);
    }

    @Override
    public synchronized V replace(K key, V value) {
        return super.replace(key, value);
    }

    @Override
    public synchronized boolean replace(K key, V expected, V value) {
        return super.replace(key, expected, value);
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return super.containsKey(key);
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        return super.containsValue(value);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    /** The mapping runs outside the lock; two threads racing on an absent key both build, the first published wins. */
    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mapping) {
        V current = get(key);
        if (current != null) {
            return current;
        }

        V created = mapping.apply(key);
        if (created == null) {
            return null;
        }

        synchronized (this) {
            V existing = super.get(key);
            if (existing != null) {
                return existing;
            }

            super.put(key, created);
            return created;
        }
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        return update(key, current -> current == null ? null : remapping.apply(key, current));
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        return update(key, current -> remapping.apply(key, current));
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        return update(key, current -> current == null ? value : remapping.apply(current, value));
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        for (Map.Entry<K, V> entry : entries()) {
            update(entry.getKey(), current -> current == null ? null : function.apply(entry.getKey(), current));
        }
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        for (Map.Entry<K, V> entry : entries()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized Set<K> keySet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(super.keySet()));
    }

    @Override
    public synchronized Collection<V> values() {
        return Collections.unmodifiableList(new ArrayList<>(super.values()));
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(entries()));
    }

    @Override
    public synchronized boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public synchronized int hashCode() {
        return super.hashCode();
    }

    @Override
    public synchronized String toString() {
        return super.toString();
    }

    @Override
    public synchronized Object clone() {
        return super.clone();
    }

    /** Applies the change to the value read outside the lock, and publishes only if the entry still holds that value, else reads again. */
    private V update(K key, UnaryOperator<V> change) {
        while (true) {
            V current = get(key);
            V next = change.apply(current);
            synchronized (this) {
                if (super.get(key) != current) {
                    continue;
                }

                if (next == null) {
                    super.remove(key);
                } else {
                    super.put(key, next);
                }

                return next;
            }
        }
    }

    private synchronized List<Map.Entry<K, V>> entries() {
        List<Map.Entry<K, V>> copy = new ArrayList<>(super.size());
        for (Map.Entry<K, V> entry : super.entrySet()) {
            copy.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }

        return copy;
    }
}
