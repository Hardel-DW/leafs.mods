package fr.hardel.excess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SynchronizedHashSet<E> extends HashSet<E> {

    @Override
    public synchronized boolean add(E element) {
        return super.add(element);
    }

    @Override
    public synchronized boolean remove(Object element) {
        return super.remove(element);
    }

    @Override
    public synchronized boolean contains(Object element) {
        return super.contains(element);
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
    public synchronized boolean addAll(Collection<? extends E> elements) {
        return super.addAll(elements);
    }

    @Override
    public synchronized boolean removeAll(Collection<?> elements) {
        return super.removeAll(elements);
    }

    @Override
    public synchronized boolean retainAll(Collection<?> elements) {
        return super.retainAll(elements);
    }

    @Override
    public synchronized boolean containsAll(Collection<?> elements) {
        return super.containsAll(elements);
    }

    @Override
    public synchronized Object[] toArray() {
        return super.toArray();
    }

    @Override
    public synchronized <T> T[] toArray(T[] array) {
        return super.toArray(array);
    }

    @Override
    public Iterator<E> iterator() {
        return new SnapshotIterator<>(snapshot().iterator(), this::remove);
    }

    @Override
    public Spliterator<E> spliterator() {
        return snapshot().spliterator();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        snapshot().forEach(action);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return Snapshots.removeIf(this, snapshot(), filter);
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

    private synchronized List<E> snapshot() {
        List<E> copy = new ArrayList<>(super.size());
        Iterator<E> live = super.iterator();
        while (live.hasNext()) {
            copy.add(live.next());
        }

        return copy;
    }
}
