package fr.hardel.shared;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An entry of a copy the facade shares with other walks: setValue writes the live map and keeps the new value here, never in the copy.
 * An entry of java.util is this class itself, a fastutil entry keeps its typed interface through a proxy.
 */
final class SnapshotEntry implements Map.Entry<Object, Object>, InvocationHandler {
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

    static Object of(Object lock, Map<?, ?> live, Map.Entry<?, ?> copy) {
        SnapshotEntry entry = new SnapshotEntry(lock, live, copy);
        if (copy.getClass().getPackageName().equals("java.util")) {
            return entry;
        }

        Class<?>[] interfaces = interfacesOf(copy.getClass()).toArray(Class<?>[]::new);
        return Proxy.newProxyInstance(Reflection.loaderOf(copy.getClass()), interfaces, entry);
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
    public Object getKey() {
        return copy.getKey();
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public Object setValue(Object replacement) {
        Object previous = value;
        value = replacement;
        synchronized (lock) {
            Reflection.put(live, copy.getKey(), replacement);
        }

        return previous;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Map.Entry<?, ?> entry && Objects.equals(getKey(), entry.getKey()) && Objects.equals(value, entry.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getKey()) ^ Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "%s=%s".formatted(getKey(), value);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "setValue" -> setValue(arguments[0]);
            case "getValue" -> value;
            default -> Reflection.invoke(method, copy, arguments);
        };
    }
}
