package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

final class Snapshots {

    private Snapshots() {
    }

    static <E> boolean removeIf(Collection<E> live, List<E> snapshot, Predicate<? super E> filter) {
        List<E> matched = new ArrayList<>();
        for (E element : snapshot) {
            if (filter.test(element)) {
                matched.add(element);
            }
        }

        return !matched.isEmpty() && live.removeAll(matched);
    }

    static <K, V> V computeIfAbsent(Map<K, V> live, K key, Function<? super K, ? extends V> mapping) {
        V current = live.get(key);
        if (current != null) {
            return current;
        }

        V created = mapping.apply(key);
        if (created == null) {
            return null;
        }

        synchronized (live) {
            V existing = live.get(key);
            if (existing != null) {
                return existing;
            }

            live.put(key, created);
            return created;
        }
    }

    static <K, V> V update(Map<K, V> live, K key, UnaryOperator<V> change) {
        while (true) {
            V current = live.get(key);
            V next = change.apply(current);
            synchronized (live) {
                if (live.get(key) != current) {
                    continue;
                }

                if (next == null) {
                    live.remove(key);
                } else {
                    live.put(key, next);
                }

                return next;
            }
        }
    }

    static <K, V> void replaceAll(Map<K, V> live, List<Entry<K, V>> entries, BiFunction<? super K, ? super V, ? extends V> function) {
        for (Entry<K, V> entry : entries) {
            update(live, entry.getKey(), current -> current == null ? null : function.apply(entry.getKey(), current));
        }
    }

    static final class Entry<K, V> implements Object2ObjectMap.Entry<K, V>, Reference2ObjectMap.Entry<K, V> {
        private final Map<K, V> live;
        private final K key;
        private V value;

        Entry(Map<K, V> live, Map.Entry<K, V> source) {
            this.live = live;
            this.key = source.getKey();
            this.value = source.getValue();
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V previous = this.value;
            this.value = value;
            live.put(key, value);
            return previous;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Map.Entry<?, ?> entry && Objects.equals(key, entry.getKey()) && Objects.equals(value, entry.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    static final class Keys<K, V> extends AbstractObjectSet<K> implements ReferenceSet<K> {
        private final Map<K, V> live;
        private final List<Entry<K, V>> entries;

        Keys(Map<K, V> live, List<Entry<K, V>> entries) {
            this.live = live;
            this.entries = entries;
        }

        @Override
        public ObjectIterator<K> iterator() {
            return new SnapshotIterator<>(entries.stream().map(Entry::getKey).iterator(), live::remove);
        }

        @Override
        public ObjectSpliterator<K> spliterator() {
            return ObjectSpliterators.asObjectSpliterator(entries.stream().map(Entry::getKey).spliterator());
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean contains(Object key) {
            return live.containsKey(key);
        }

        @Override
        public boolean remove(Object key) {
            synchronized (live) {
                boolean present = live.containsKey(key);
                live.remove(key);
                return present;
            }
        }

        @Override
        public void clear() {
            live.clear();
        }
    }

    static final class Values<K, V> extends AbstractObjectCollection<V> {
        private final Map<K, V> live;
        private final List<Entry<K, V>> entries;

        Values(Map<K, V> live, List<Entry<K, V>> entries) {
            this.live = live;
            this.entries = entries;
        }

        @Override
        public ObjectIterator<V> iterator() {
            SnapshotIterator<Entry<K, V>> walk = new SnapshotIterator<>(entries.iterator(), entry -> live.remove(entry.getKey()));
            return new ObjectIterator<>() {
                @Override
                public boolean hasNext() {
                    return walk.hasNext();
                }

                @Override
                public V next() {
                    return walk.next().getValue();
                }

                @Override
                public void remove() {
                    walk.remove();
                }
            };
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean contains(Object value) {
            return live.containsValue(value);
        }

        @Override
        public void clear() {
            live.clear();
        }
    }

    static class Entries<K, V, E extends Map.Entry<K, V>> extends AbstractObjectSet<E> {
        private final Map<K, V> live;
        private final List<E> entries;

        Entries(Map<K, V> live, List<E> entries) {
            this.live = live;
            this.entries = entries;
        }

        @Override
        public ObjectIterator<E> iterator() {
            return new SnapshotIterator<>(entries.iterator(), entry -> live.remove(entry.getKey()));
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean contains(Object object) {
            return object instanceof Map.Entry<?, ?> entry && live.containsKey(entry.getKey()) && Objects.equals(live.get(entry.getKey()), entry.getValue());
        }

        @Override
        public void clear() {
            live.clear();
        }
    }

    static final class Object2ObjectEntries<K, V> extends Entries<K, V, Object2ObjectMap.Entry<K, V>> implements Object2ObjectMap.FastEntrySet<K, V> {
        Object2ObjectEntries(Map<K, V> live, List<Object2ObjectMap.Entry<K, V>> entries) {
            super(live, entries);
        }

        @Override
        public ObjectIterator<Object2ObjectMap.Entry<K, V>> fastIterator() {
            return iterator();
        }
    }

    static final class Reference2ObjectEntries<K, V> extends Entries<K, V, Reference2ObjectMap.Entry<K, V>> implements Reference2ObjectMap.FastEntrySet<K, V> {
        Reference2ObjectEntries(Map<K, V> live, List<Reference2ObjectMap.Entry<K, V>> entries) {
            super(live, entries);
        }

        @Override
        public ObjectIterator<Reference2ObjectMap.Entry<K, V>> fastIterator() {
            return iterator();
        }
    }
}
