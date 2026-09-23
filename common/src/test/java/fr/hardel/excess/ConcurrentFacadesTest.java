package fr.hardel.excess;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrentFacadesTest {

    @ParameterizedTest
    @ValueSource(classes = {ConcurrentInt2ObjectMap.class, ConcurrentLong2ObjectMap.class, ConcurrentShort2ObjectMap.class, ConcurrentObject2BooleanMap.class})
    void everyCompoundWriteIsRedefined(Class<?> facade) {
        List<String> inherited = Arrays.stream(facade.getMethods())
            .filter(method -> method.getDeclaringClass() != facade && !method.isAnnotationPresent(Deprecated.class) && isCompound(method))
            .map(Method::toGenericString)
            .toList();

        assertEquals(List.of(), inherited);
    }

    private static boolean isCompound(Method method) {
        String name = method.getName();
        return name.startsWith("compute") || name.startsWith("merge") || name.startsWith("replace") || name.equals("putIfAbsent")
            || name.equals("remove") && method.getParameterCount() == 2;
    }
}
