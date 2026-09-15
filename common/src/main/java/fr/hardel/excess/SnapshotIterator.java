package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Iterator;
import java.util.function.Consumer;

/** Walks a copy taken under the collection's lock; remove goes back to the live collection through the remover. */
final class SnapshotIterator<E> implements ObjectIterator<E> {
    private final Iterator<E> snapshot;
    private final Consumer<E> remover;
    private E last;
    private boolean removable;

    SnapshotIterator(Iterator<E> snapshot, Consumer<E> remover) {
        this.snapshot = snapshot;
        this.remover = remover;
    }

    @Override
    public boolean hasNext() {
        return snapshot.hasNext();
    }

    @Override
    public E next() {
        last = snapshot.next();
        removable = true;
        return last;
    }

    @Override
    public void remove() {
        if (!removable) {
            throw new IllegalStateException();
        }

        removable = false;
        remover.accept(last);
    }
}
