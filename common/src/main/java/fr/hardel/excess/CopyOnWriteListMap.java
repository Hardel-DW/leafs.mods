package fr.hardel.excess;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** A concurrent map whose values are always copy-on-write lists, whatever list the writer stores. A list already copy-on-write keeps its identity. */
public final class CopyOnWriteListMap<K, T> extends ConcurrentHashMap<K, List<T>> {

    @Override
    public List<T> put(K key, List<T> value) {
        return super.put(key, copyOnWrite(value));
    }

    @Override
    public List<T> computeIfAbsent(K key, Function<? super K, ? extends List<T>> mapping) {
        return super.computeIfAbsent(key, k -> copyOnWrite(mapping.apply(k)));
    }

    private static <T> List<T> copyOnWrite(List<T> list) {
        return list instanceof CopyOnWriteArrayList<T> ? list : new CopyOnWriteArrayList<>(list);
    }
}
