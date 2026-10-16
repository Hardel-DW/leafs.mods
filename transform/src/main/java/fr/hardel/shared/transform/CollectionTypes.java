package fr.hardel.shared.transform;

import java.util.List;

/** A top level class of java.util or fastutil named like a collection. The runtime still decides whether it needs a facade. */
final class CollectionTypes {
    private static final List<String> SUFFIXES = List.of("Map", "Set", "List", "Queue", "Deque");
    private static final String JAVA_UTIL = "java/util/";

    private CollectionTypes() {
    }

    static boolean isCollection(String type) {
        boolean library = (type.startsWith(JAVA_UTIL) && type.indexOf('/', JAVA_UTIL.length()) < 0) || type.startsWith("it/unimi/dsi/fastutil/");
        return library && type.indexOf('$') < 0 && SUFFIXES.stream().anyMatch(type::endsWith);
    }
}
