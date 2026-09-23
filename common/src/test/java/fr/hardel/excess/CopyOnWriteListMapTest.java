package fr.hardel.excess;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-08-30: Lithium overwrites ClassInstanceMultiMap.find, so the class lists must become copy-on-write in the map, never at a call site inside find. */
class CopyOnWriteListMapTest {

    @Test
    void aPlainListIsStoredCopyOnWriteAndACopyOnWriteOneKeepsItsIdentity() {
        CopyOnWriteListMap<String, Integer> map = new CopyOnWriteListMap<>();
        List<Integer> base = new CopyOnWriteArrayList<>(List.of(1));

        map.put("base", base);
        map.put("plain", new ArrayList<>(List.of(2)));
        map.computeIfAbsent("computed", _ -> new ArrayList<>(List.of(3)));

        assertSame(base, map.get("base"), "the base list is the one the owner appends to, its identity must survive the put");
        assertInstanceOf(CopyOnWriteArrayList.class, map.get("plain"));
        assertInstanceOf(CopyOnWriteArrayList.class, map.get("computed"));
    }

    @Test
    void everyWriteStoresACopyOnWriteList() {
        List<Consumer<CopyOnWriteListMap<String, Integer>>> writes = List.of(
            map -> map.putIfAbsent("absent", plain()),
            map -> map.putAll(Map.of("key", plain())),
            map -> map.replace("key", plain()),
            map -> map.replace("key", map.get("key"), plain()),
            map -> map.replaceAll((_, _) -> plain()),
            map -> map.compute("key", (_, _) -> plain()),
            map -> map.computeIfPresent("key", (_, _) -> plain()),
            map -> map.merge("absent", plain(), (_, _) -> plain()),
            map -> map.merge("key", plain(), (_, _) -> plain()),
            map -> map.keySet(plain()).add("absent"),
            map -> map.entrySet().iterator().next().setValue(plain())
        );

        for (Consumer<CopyOnWriteListMap<String, Integer>> write : writes) {
            CopyOnWriteListMap<String, Integer> map = new CopyOnWriteListMap<>();
            map.put("key", new CopyOnWriteArrayList<>());
            write.accept(map);
            map.values().forEach(list -> assertInstanceOf(CopyOnWriteArrayList.class, list));
        }
    }

    @Test
    void aNullMappingStoresNothing() {
        CopyOnWriteListMap<String, Integer> map = new CopyOnWriteListMap<>();
        map.put("key", plain());

        assertNull(map.computeIfAbsent("absent", _ -> null));
        assertNull(map.compute("key", (_, _) -> null));
        assertTrue(map.isEmpty());
    }

    private static List<Integer> plain() {
        return new ArrayList<>(List.of(1));
    }
}
