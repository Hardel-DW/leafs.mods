package fr.hardel.shared;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

final class Reflection {
    private static final Method PUT = mapPut();

    private Reflection() {
    }

    static Object invoke(Method method, Object target, Object[] arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException thrown) {
            throw rethrow(thrown.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Leafs could not reach %s of a shared collection".formatted(method), failure);
        }
    }

    static void put(Map<?, ?> map, Object key, Object value) {
        invoke(PUT, map, new Object[]{key, value});
    }

    static ClassLoader loaderOf(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        return loader == null ? Reflection.class.getClassLoader() : loader;
    }

    private static RuntimeException rethrow(Throwable thrown) {
        if (thrown instanceof RuntimeException runtime) {
            throw runtime;
        }

        if (thrown instanceof Error error) {
            throw error;
        }

        return new IllegalStateException("A shared collection failed", thrown);
    }

    private static Method mapPut() {
        try {
            return Map.class.getMethod("put", Object.class, Object.class);
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
