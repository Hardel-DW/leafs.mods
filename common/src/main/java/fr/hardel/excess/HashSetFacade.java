package fr.hardel.excess;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;

public final class HashSetFacade<E> extends HashSet<E> {
    private final Set<E> set = ConcurrentHashMap.newKeySet();

    public HashSetFacade(Collection<? extends E> source) {
        set.addAll(source);
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object element) {
        return set.contains(element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        return set.containsAll(elements);
    }

    @Override
    public boolean add(E element) {
        return set.add(element);
    }

    @Override
    public boolean addAll(Collection<? extends E> elements) {
        return set.addAll(elements);
    }

    @Override
    public boolean remove(Object element) {
        return set.remove(element);
    }

    @Override
    public boolean removeAll(Collection<?> elements) {
        return set.removeAll(elements);
    }

    @Override
    public boolean retainAll(Collection<?> elements) {
        return set.retainAll(elements);
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public Iterator<E> iterator() {
        return set.iterator();
    }

    @Override
    public Spliterator<E> spliterator() {
        return set.spliterator();
    }

    @Override
    public Object[] toArray() {
        return set.toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
        return set.toArray(array);
    }

    @Override
    public boolean equals(Object other) {
        return set.equals(other);
    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }

    @Override
    public String toString() {
        return set.toString();
    }

    @Override
    public HashSetFacade<E> clone() {
        return new HashSetFacade<>(set);
    }
}
