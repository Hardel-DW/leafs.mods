package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.Reference2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class SynchronizedReference2ObjectOpenHashMap<K, V> extends Reference2ObjectOpenHashMap<K, V> {

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

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mapping) {
        return Snapshots.computeIfAbsent(this, key, mapping);
    }

    @Override
    public V computeIfAbsent(K key, Reference2ObjectFunction<? super K, ? extends V> mapping) {
        return Snapshots.computeIfAbsent(this, key, mapping);
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        return Snapshots.update(this, key, current -> current == null ? null : remapping.apply(key, current));
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        return Snapshots.update(this, key, current -> remapping.apply(key, current));
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        return Snapshots.update(this, key, current -> current == null ? value : remapping.apply(current, value));
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        for (Snapshots.Entry<K, V> entry : entries()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public ReferenceSet<K> keySet() {
        return new Snapshots.Keys<>(this, entries());
    }

    @Override
    public ObjectCollection<V> values() {
        return new Snapshots.Values<>(this, entries());
    }

    @Override
    public FastEntrySet<K, V> reference2ObjectEntrySet() {
        return new Snapshots.Reference2ObjectEntries<>(this, new ArrayList<Reference2ObjectMap.Entry<K, V>>(entries()));
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
    public synchronized Reference2ObjectOpenHashMap<K, V> clone() {
        return super.clone();
    }

    private synchronized List<Snapshots.Entry<K, V>> entries() {
        List<Snapshots.Entry<K, V>> copy = new ArrayList<>(super.size());
        for (Reference2ObjectMap.Entry<K, V> entry : super.reference2ObjectEntrySet()) {
            copy.add(new Snapshots.Entry<>(this, entry));
        }

        return copy;
    }
}
