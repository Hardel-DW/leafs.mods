package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectCollections;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * An Object2ObjectOpenHashMap other threads may write while one iterates: writes take the map's lock, the key and value views are unmodifiable
 * snapshots, the entry set is a detached copy, and the functions of compute and merge run outside the lock, published only if the entry did not move
 * meanwhile. Fits a field declared Object2ObjectOpenHashMap or Map.
 */
public final class SynchronizedObject2ObjectOpenHashMap<K, V> extends Object2ObjectOpenHashMap<K, V> {

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

    @Override
    public synchronized boolean trim() {
        return super.trim();
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
    public V computeIfAbsent(K key, Object2ObjectFunction<? super K, ? extends V> mapping) {
        return computeIfAbsent(key, (Function<? super K, ? extends V>) mapping);
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
    public void forEach(BiConsumer<? super K, ? super V> action) {
        for (Object2ObjectMap.Entry<K, V> entry : copy().object2ObjectEntrySet()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized ObjectSet<K> keySet() {
        return ObjectSets.unmodifiable(new ObjectOpenHashSet<>(super.keySet()));
    }

    @Override
    public synchronized ObjectCollection<V> values() {
        return ObjectCollections.unmodifiable(new ObjectArrayList<>(super.values()));
    }

    @Override
    public FastEntrySet<K, V> object2ObjectEntrySet() {
        return copy().object2ObjectEntrySet();
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
    public synchronized Object2ObjectOpenHashMap<K, V> clone() {
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

    private synchronized Object2ObjectOpenHashMap<K, V> copy() {
        Object2ObjectOpenHashMap<K, V> copy = new Object2ObjectOpenHashMap<>(super.size());
        for (Object2ObjectMap.Entry<K, V> entry : super.object2ObjectEntrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }

        return copy;
    }
}
