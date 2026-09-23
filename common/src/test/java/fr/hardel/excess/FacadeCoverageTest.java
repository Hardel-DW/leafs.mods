package fr.hardel.excess;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacadeCoverageTest {
    private static final Set<String> SEQUENCED = Set.of("addFirst", "addLast", "getFirst", "getLast", "removeFirst", "removeLast", "reversed");

    @ParameterizedTest
    @ValueSource(classes = {
        ConcurrentInt2ObjectMap.class, ConcurrentLong2ObjectMap.class, ConcurrentShort2ObjectMap.class, ConcurrentObject2BooleanMap.class,
        ConcurrentLongSet.class, ConcurrentOrderedLongSet.class
    })
    void aConcurrentFacadeRedefinesEveryPrimitiveCompoundWrite(Class<?> facade) {
        assertEquals(List.of(), inherited(facade, method -> method.getDeclaringClass() != Map.class && isCompound(method)));
    }

    @ParameterizedTest
    @ValueSource(classes = {
        HashMapFacade.class, HashSetFacade.class, SynchronizedArrayList.class,
        SynchronizedLongOpenHashSet.class, SynchronizedObject2ObjectOpenHashMap.class
    })
    void aClassFacadeRedefinesEveryMethodOfItsClasses(Class<?> facade) {
        assertEquals(List.of(), inherited(facade, method -> !method.getDeclaringClass().isInterface() || isCompound(method) || SEQUENCED.contains(method.getName())));
    }

    private static List<String> inherited(Class<?> facade, Predicate<Method> mustRedefine) {
        return Arrays.stream(facade.getMethods())
            .filter(method -> method.getDeclaringClass() != facade && method.getDeclaringClass() != Object.class)
            .filter(method -> (method.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) == 0 && !method.isAnnotationPresent(Deprecated.class))
            .filter(mustRedefine)
            .map(Method::toGenericString)
            .toList();
    }

    private static boolean isCompound(Method method) {
        String name = method.getName();
        return name.startsWith("compute") || name.startsWith("merge") || name.startsWith("replace") || name.equals("putIfAbsent")
            || name.equals("remove") && method.getParameterCount() == 2;
    }
}
