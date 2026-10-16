package fr.hardel.shared;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Walks a copy, and removes from the live collection what it last returned. A plain Iterator is this class itself, read by direct calls:
 * a mod walking a shared list with a for loop pays no reflection. Only a richer iterator, a ListIterator or a fastutil one, goes through a proxy.
 */
final class SnapshotIterator implements Iterator<Object>, InvocationHandler {
    private final Object lock;
    private final Object live;
    private final Iterator<?> copy;
    private Object last;
    private boolean returned;

    private SnapshotIterator(Object lock, Object live, Iterator<?> copy) {
        this.lock = lock;
        this.live = live;
        this.copy = copy;
    }

    /** A spliterator only reads, it walks the copy as it is. */
    static Object over(Object lock, Object live, Class<?> api, Object walk) {
        if (!(walk instanceof Iterator<?> iterator)) {
            return walk;
        }

        SnapshotIterator snapshot = new SnapshotIterator(lock, live, iterator);
        return api == Iterator.class ? snapshot : Proxy.newProxyInstance(Reflection.loaderOf(api), new Class<?>[]{api}, snapshot);
    }

    @Override
    public boolean hasNext() {
        return copy.hasNext();
    }

    @Override
    public Object next() {
        return returning(copy.next());
    }

    @Override
    public void remove() {
        if (!returned) {
            throw new IllegalStateException("remove() without a returned element");
        }

        synchronized (lock) {
            ((Collection<?>) live).remove(last);
            ((SharedCollection) lock).leafs$changed();
        }

        returned = false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
        String name = method.getName();
        if (name.equals("remove") && method.getParameterCount() == 0) {
            remove();
            return null;
        }

        if (name.equals("set") || name.equals("add")) {
            throw new UnsupportedOperationException("An iterator of a shared collection walks a copy, use the collection itself to %s".formatted(name));
        }

        Object result = Reflection.invoke(method, copy, arguments);
        boolean moves = name.startsWith("next") || name.startsWith("previous");
        return moves && method.getReturnType() != void.class ? returning(result) : result;
    }

    private Object returning(Object element) {
        last = element;
        returned = true;
        return element instanceof Map.Entry<?, ?> entry && lock instanceof Map<?, ?> map ? SnapshotEntry.of(lock, map, entry) : element;
    }
}
