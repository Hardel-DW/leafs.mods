package fr.hardel.excess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SynchronizedArrayDeque<E> extends ArrayDeque<E> {

    @Override
    public synchronized boolean add(E element) {
        return super.add(element);
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
    public synchronized boolean offer(E element) {
        return super.offer(element);
    }

    @Override
    public synchronized boolean offerFirst(E element) {
        return super.offerFirst(element);
    }

    @Override
    public synchronized boolean offerLast(E element) {
        return super.offerLast(element);
    }

    @Override
    public synchronized void push(E element) {
        super.push(element);
    }

    @Override
    public synchronized E pop() {
        return super.pop();
    }

    @Override
    public synchronized E poll() {
        return super.poll();
    }

    @Override
    public synchronized E pollFirst() {
        return super.pollFirst();
    }

    @Override
    public synchronized E pollLast() {
        return super.pollLast();
    }

    @Override
    public synchronized E peek() {
        return super.peek();
    }

    @Override
    public synchronized E peekFirst() {
        return super.peekFirst();
    }

    @Override
    public synchronized E peekLast() {
        return super.peekLast();
    }

    @Override
    public synchronized E remove() {
        return super.remove();
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
    public synchronized boolean remove(Object element) {
        return super.remove(element);
    }

    @Override
    public synchronized boolean removeFirstOccurrence(Object element) {
        return super.removeFirstOccurrence(element);
    }

    @Override
    public synchronized boolean removeLastOccurrence(Object element) {
        return super.removeLastOccurrence(element);
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
    public synchronized E element() {
        return super.element();
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
    public synchronized Deque<E> reversed() {
        return super.reversed();
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
    public Iterator<E> descendingIterator() {
        List<E> reversed = snapshot().reversed();
        return new SnapshotIterator<>(reversed.iterator(), this::remove);
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
    public synchronized String toString() {
        return super.toString();
    }

    @Override
    public synchronized ArrayDeque<E> clone() {
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
