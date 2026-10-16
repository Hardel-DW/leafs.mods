package fr.hardel.shared;

import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.stream.BaseStream;
import java.util.stream.Collectors;

/** The views of a facade: live, locked on the facade, and walked on a copy. */
final class Views {
    private static final List<Class<?>> VIEW_TYPES = List.of(Iterable.class, Iterator.class, Spliterator.class, BaseStream.class, Map.class);
    private static final ClassValue<Map<String, Method>> ORIGINS = new ClassValue<>() {
        @Override
        protected Map<String, Method> computeValue(Class<?> type) {
            return Arrays.stream(type.getMethods()).collect(Collectors.toMap(method -> key(method.getName(), Type.getMethodDescriptor(method)), method -> method, (first, _) -> first));
        }
    };

    private Views() {
    }

    static boolean isView(Method method) {
        Class<?> result = method.getReturnType();
        return result.isInterface() && VIEW_TYPES.stream().anyMatch(view -> view.isAssignableFrom(result));
    }

    static Object of(Object facade, String name, String descriptor, Object[] arguments, Object view) {
        if (view == null) {
            return null;
        }

        Method origin = ORIGINS.get(facade.getClass().getSuperclass()).get(key(name, descriptor));
        return LockedView.proxy(facade, () -> ((SharedCollection) facade).leafs$snapshot(), origin, arguments, view);
    }

    private static String key(String name, String descriptor) {
        return "%s%s".formatted(name, descriptor);
    }
}
