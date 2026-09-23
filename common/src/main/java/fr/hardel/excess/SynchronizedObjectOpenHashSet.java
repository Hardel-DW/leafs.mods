package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SynchronizedObjectOpenHashSet<E> extends ObjectOpenHashSet<E> {

    public SynchronizedObjectOpenHashSet() {
    }

    public SynchronizedObjectOpenHashSet(Collection<? extends E> elements) {
        super(elements);
    }

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
    public synchronized boolean trim() {
        return super.trim();
    }

    @Override
    public ObjectIterator<E> iterator() {
        return new SnapshotIterator<>(snapshot().iterator(), this::remove);
    }

    @Override
    public ObjectSpliterator<E> spliterator() {
        return ObjectSpliterators.asObjectSpliterator(snapshot().spliterator());
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
    public synchronized ObjectOpenHashSet<E> clone() {
        return super.clone();
    }

    private synchronized List<E> snapshot() {
        List<E> copy = new ArrayList<>(super.size());
        ObjectIterator<E> live = super.iterator();
        while (live.hasNext()) {
            copy.add(live.next());
        }

        return copy;
    }
}
