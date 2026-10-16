package fr.hardel.shared;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/** Walks a copy, and removes from the live collection what it last returned. */
final class SnapshotIterator implements InvocationHandler {
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

    static Object proxy(Object lock, Object live, Class<?> api, Iterator<?> copy) {
        return Proxy.newProxyInstance(Reflection.loaderOf(api), new Class<?>[]{api}, new SnapshotIterator(lock, live, copy));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
        String name = method.getName();
        if (name.equals("remove") && method.getParameterCount() == 0) {
            removeLast();
            return null;
        }

        if (name.equals("set") || name.equals("add")) {
            throw new UnsupportedOperationException("An iterator of a shared collection walks a copy, use the collection itself to %s".formatted(name));
        }

        Object result = Reflection.invoke(method, copy, arguments);
        if (method.getReturnType() == void.class || !(name.startsWith("next") || name.startsWith("previous"))) {
            return result;
        }

        last = result;
        returned = true;
        return result instanceof Map.Entry<?, ?> entry && lock instanceof Map<?, ?> map ? SnapshotEntry.proxy(lock, map, entry) : result;
    }

    private void removeLast() {
        if (!returned) {
            throw new IllegalStateException("remove() without a returned element");
        }

        synchronized (lock) {
            ((Collection<?>) live).remove(last);
        }

        returned = false;
    }
}
