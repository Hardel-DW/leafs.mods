package fr.hardel.shared;

import java.util.Set;

/** The methods that only read a collection. Any other method may change it and drops the copy a facade keeps, an unknown name costs a copy, never a stale walk. */
final class Reads {
    private static final Set<String> NAMES = Set.of("get", "getOrDefault", "containsKey", "containsValue", "contains", "containsAll", "size", "isEmpty", "peek", "peekFirst",
        "peekLast", "element", "getFirst", "getLast", "first", "last", "firstKey", "lastKey", "floorKey", "ceilingKey", "higherKey", "lowerKey", "floor", "ceiling", "higher",
        "lower", "firstEntry", "lastEntry", "floorEntry", "ceilingEntry", "higherEntry", "lowerEntry", "indexOf", "lastIndexOf", "equals", "hashCode", "toString", "comparator",
        "toArray", "getLong", "getInt", "getShort", "getByte", "getChar", "getFloat", "getDouble", "getBoolean");

    private Reads() {
    }

    static boolean only(String method) {
        return NAMES.contains(method);
    }
}
