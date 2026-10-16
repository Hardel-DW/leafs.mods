package fr.hardel.shared;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;

/** A view of a facade. Reads and writes go to the live view under the facade monitor, a walk goes over the same view of a copy. */
final class LockedView implements InvocationHandler {
    private static final Set<String> WALKS = Set.of("iterator", "listIterator", "spliterator", "stream", "parallelStream");

    private final Object lock;
    private final Supplier<Object> copies;
    private final Method origin;
    private final Object[] arguments;
    private final Object live;

    private LockedView(Object lock, Supplier<Object> copies, Method origin, Object[] arguments, Object live) {
        this.lock = lock;
        this.copies = copies;
        this.origin = origin;
        this.arguments = arguments;
        this.live = live;
    }

    static Object proxy(Object lock, Supplier<Object> copies, Method origin, Object[] arguments, Object live) {
        Class<?> api = origin.getReturnType();
        return Proxy.newProxyInstance(Reflection.loaderOf(api), new Class<?>[]{api}, new LockedView(lock, copies, origin, arguments, live));
    }

    private Object copy() {
        return Reflection.invoke(origin, copies.get(), arguments);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] callArguments) {
        if (Thread.holdsLock(lock)) {
            return Reflection.invoke(method, live, callArguments);
        }

        if (WALKS.contains(method.getName()) || method.getName().startsWith("forEach")) {
            return walk(method, callArguments);
        }

        Object result;
        synchronized (lock) {
            result = Reflection.invoke(method, live, callArguments);
        }

        return result != null && Views.isView(method) ? proxy(lock, this::copy, method, callArguments, result) : result;
    }

    private Object walk(Method method, Object[] callArguments) {
        Object result = Reflection.invoke(method, copy(), callArguments);
        return result instanceof Iterator<?> iterator ? SnapshotIterator.proxy(lock, live, method.getReturnType(), iterator) : result;
    }
}
