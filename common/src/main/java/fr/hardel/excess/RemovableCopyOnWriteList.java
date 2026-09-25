package fr.hardel.excess;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RemovableCopyOnWriteList<E> extends CopyOnWriteArrayList<E> {

    @Override
    public Iterator<E> iterator() {
        Iterator<E> snapshot = super.iterator();
        return new Iterator<>() {
            private E last;

            @Override
            public boolean hasNext() {
                return snapshot.hasNext();
            }

            @Override
            public E next() {
                last = snapshot.next();
                return last;
            }

            @Override
            public void remove() {
                RemovableCopyOnWriteList.this.remove(last);
            }
        };
    }
}
