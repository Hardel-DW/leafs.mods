package fr.hardel.leafs.entity;

import fr.hardel.excess.CopyOnWriteListMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
