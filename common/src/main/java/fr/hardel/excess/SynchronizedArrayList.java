package fr.hardel.excess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class SynchronizedArrayList<E> extends ArrayList<E> {

    @Override
    public synchronized boolean add(E element) {
        return super.add(element);
    }

    @Override
    public synchronized void add(int index, E element) {
        super.add(index, element);
    }

    @Override
    public synchronized E remove(int index) {
        return super.remove(index);
    }

    @Override
    public synchronized boolean remove(Object element) {
        return super.remove(element);
    }

    @Override
    public synchronized E get(int index) {
        return super.get(index);
    }

    @Override
    public synchronized E set(int index, E element) {
        return super.set(index, element);
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
    public synchronized boolean contains(Object element) {
        return super.contains(element);
    }

    @Override
    public synchronized int indexOf(Object element) {
        return super.indexOf(element);
    }

    @Override
    public synchronized int lastIndexOf(Object element) {
        return super.lastIndexOf(element);
    }

    @Override
    public synchronized boolean addAll(Collection<? extends E> elements) {
        return super.addAll(elements);
    }

    @Override
    public synchronized boolean addAll(int index, Collection<? extends E> elements) {
        return super.addAll(index, elements);
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
    public synchronized void replaceAll(UnaryOperator<E> operator) {
        super.replaceAll(operator);
    }

    @Override
    public synchronized void sort(Comparator<? super E> comparator) {
        super.sort(comparator);
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
    public synchronized E getFirst() {
        return super.getFirst();
    }

    @Override
    public synchronized E getLast() {
        return super.getLast();
    }

    @Override
    public synchronized void addFirst(E element) {
        super.addFirst(element);
    }

    @Override
    public synchronized void addLast(E element) {
        super.addLast(element);
    }

    @Override
    public synchronized E removeFirst() {
        return super.removeFirst();
    }

    @Override
    public synchronized E removeLast() {
        return super.removeLast();
    }

    @Override
    public synchronized List<E> reversed() {
        return super.reversed();
    }

    @Override
    public synchronized void ensureCapacity(int capacity) {
        super.ensureCapacity(capacity);
    }

    @Override
    public synchronized void trimToSize() {
        super.trimToSize();
    }

    @Override
    public Iterator<E> iterator() {
        return new SnapshotIterator<>(snapshot().iterator(), element -> remove((Object) element));
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new SnapshotListIterator(index);
    }

    @Override
    public List<E> subList(int from, int to) {
        return Collections.unmodifiableList(snapshot().subList(from, to));
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
        return new ArrayList<>(super.subList(0, super.size()));
    }

    private final class SnapshotListIterator implements ListIterator<E> {
        private final ListIterator<E> cursor;
        private int expectedModCount;
        private int lastIndex = -1;

        private SnapshotListIterator(int index) {
            synchronized (SynchronizedArrayList.this) {
                cursor = snapshot().listIterator(index);
                expectedModCount = modCount;
            }
        }

        @Override
        public boolean hasNext() {
            return cursor.hasNext();
        }

        @Override
        public E next() {
            lastIndex = cursor.nextIndex();
            return cursor.next();
        }

        @Override
        public boolean hasPrevious() {
            return cursor.hasPrevious();
        }

        @Override
        public E previous() {
            lastIndex = cursor.previousIndex();
            return cursor.previous();
        }

        @Override
        public int nextIndex() {
            return cursor.nextIndex();
        }

        @Override
        public int previousIndex() {
            return cursor.previousIndex();
        }

        @Override
        public void remove() {
            write(() -> {
                cursor.remove();
                SynchronizedArrayList.this.remove(lastIndex);
                lastIndex = -1;
            });
        }

        @Override
        public void set(E element) {
            write(() -> {
                cursor.set(element);
                SynchronizedArrayList.this.set(lastIndex, element);
            });
        }

        @Override
        public void add(E element) {
            write(() -> {
                int index = cursor.nextIndex();
                cursor.add(element);
                SynchronizedArrayList.this.add(index, element);
                lastIndex = -1;
            });
        }

        private void write(Runnable change) {
            synchronized (SynchronizedArrayList.this) {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException();
                }

                change.run();
                expectedModCount = modCount;
            }
        }
    }
}
