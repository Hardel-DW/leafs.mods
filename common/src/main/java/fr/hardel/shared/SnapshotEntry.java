package fr.hardel.shared;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** An entry of a copy the facade shares with other walks: setValue writes the live map and keeps the new value here, never in the copy. */
final class SnapshotEntry implements InvocationHandler {
    private final Object lock;
    private final Map<?, ?> live;
    private final Map.Entry<?, ?> copy;
    private Object value;

    private SnapshotEntry(Object lock, Map<?, ?> live, Map.Entry<?, ?> copy) {
        this.lock = lock;
        this.live = live;
        this.copy = copy;
        this.value = copy.getValue();
    }

    static Object proxy(Object lock, Map<?, ?> live, Map.Entry<?, ?> copy) {
        Class<?>[] interfaces = interfacesOf(copy.getClass()).toArray(Class<?>[]::new);
        return Proxy.newProxyInstance(Reflection.loaderOf(copy.getClass()), interfaces, new SnapshotEntry(lock, live, copy));
    }

    private static Set<Class<?>> interfacesOf(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            collect(current.getInterfaces(), interfaces);
        }

        return interfaces;
    }

    private static void collect(Class<?>[] found, Set<Class<?>> interfaces) {
        for (Class<?> type : found) {
            if (Modifier.isPublic(type.getModifiers()) && interfaces.add(type)) {
                collect(type.getInterfaces(), interfaces);
            }
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "setValue" -> replace(arguments[0]);
            case "getValue" -> value;
            default -> Reflection.invoke(method, copy, arguments);
        };
    }

    private Object replace(Object replacement) {
        Object previous = value;
        value = replacement;
        synchronized (lock) {
            Reflection.put(live, copy.getKey(), replacement);
        }

        return previous;
    }
}
