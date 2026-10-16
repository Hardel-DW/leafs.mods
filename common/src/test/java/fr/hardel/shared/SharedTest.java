package fr.hardel.shared;

import fr.hardel.TestThreads;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.DayOfWeek;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedTest {

    @Test
    void everyMutableLibraryCollectionGetsAFacadeOfItsOwnClass() {
        List<Object> collections = List.of(new HashMap<>(Map.of("a", 1)), new LinkedHashMap<>(Map.of("a", 1)), new TreeMap<>(Map.of("a", 1)), new WeakHashMap<>(),
            new IdentityHashMap<>(), new EnumMap<>(Map.of(DayOfWeek.MONDAY, 1)), new HashSet<>(Set.of(1)), new LinkedHashSet<>(Set.of(1)), new TreeSet<>(Set.of(1)),
            new ArrayList<>(List.of(1)), new LinkedList<>(List.of(1)), new ArrayDeque<>(List.of(1)), new PriorityQueue<>(List.of(1)), new Object2ObjectOpenHashMap<>(Map.of("a", 1)),
            new Long2ObjectOpenHashMap<>(), new LongOpenHashSet(), new ObjectArrayList<>(List.of(1)), new Int2IntOpenHashMap(), new ObjectOpenHashSet<>(), new Object2IntOpenHashMap<>(),
            new LongArrayList(), new Reference2ObjectOpenHashMap<>());

        for (Object collection : collections) {
            Object shared = Shared.wrap(collection);
            assertInstanceOf(SharedCollection.class, shared, collection.getClass().getName());
            assertSame(collection.getClass(), shared.getClass().getSuperclass());
            assertEquals(contents(collection), contents(shared), collection.getClass().getName());
        }
    }

    /** 2026-10-16: ATM 11 crashed with a StackOverflowError, the copy ArrayDeque(Collection) takes walks the facade with forEach, which copied it again. */
    @Test
    void everyFacadeWalksAndCopiesItself() {
        List<Object> collections = List.of(new HashMap<>(Map.of("a", 1)), new LinkedHashMap<>(Map.of("a", 1)), new TreeMap<>(Map.of("a", 1)), new HashSet<>(Set.of(1)),
            new TreeSet<>(Set.of(1)), new ArrayList<>(List.of(1)), new LinkedList<>(List.of(1)), new ArrayDeque<>(List.of(1)), new PriorityQueue<>(List.of(1)),
            new Object2ObjectOpenHashMap<>(Map.of("a", 1)), new ObjectArrayList<>(List.of(1)), new ObjectOpenHashSet<>(Set.of(1)), new LongArrayList(new long[]{1}));

        for (Object collection : collections) {
            Object shared = Shared.wrap(collection);
            Object expected = contents(collection);
            assertEquals(expected, contents(((SharedCollection) shared).leafs$snapshot()), collection.getClass().getName());
            assertEquals(expected, walked(shared), collection.getClass().getName());
        }
    }

    @Test
    void aFacadeRedefinesEveryMethodItsClassDeclares() {
        List<Object> collections = List.of(new HashMap<>(), new TreeMap<>(), new ArrayList<>(), new Long2ObjectOpenHashMap<>(), new ObjectOpenHashSet<>());

        for (Object collection : collections) {
            Class<?> facade = Shared.wrap(collection).getClass();
            List<String> inherited = Arrays.stream(facade.getMethods())
                .filter(method -> method.getDeclaringClass() != facade && !method.getDeclaringClass().isInterface() && method.getDeclaringClass() != Object.class)
                .filter(method -> (method.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) == 0 && !method.isBridge())
                .map(Method::toGenericString)
                .toList();
            assertEquals(List.of(), inherited, collection.getClass().getName());
        }
    }

    @Test
    void aCollectionThatNeedsNothingStaysTheSame() {
        List<Object> kept = List.of(List.of(1), new ConcurrentHashMap<>(), Collections.synchronizedList(new ArrayList<>()), new HashMap<String, Integer>() {
        }, "not a collection");

        kept.forEach(collection -> assertSame(collection, Shared.wrap(collection)));
        assertSame(null, Shared.wrap(null));
    }

    @Test
    void aSharedCollectionKeepsTheContractOfItsClass() {
        Map<String, Integer> nulls = shared(new HashMap<>());
        nulls.put(null, 1);
        nulls.put("a", null);
        assertEquals(1, nulls.get(null));
        assertTrue(nulls.containsKey("a"));

        Map<String, Integer> ordered = shared(new LinkedHashMap<>());
        List.of("c", "a", "b").forEach(key -> ordered.put(key, 0));
        assertEquals(List.of("c", "a", "b"), new ArrayList<>(ordered.keySet()));
    }

    @Test
    void concurrentWritesLoseNothing() throws InterruptedException {
        Map<Integer, Integer> map = shared(new HashMap<>());
        List<Integer> list = shared(new ArrayList<>());
        List<Thread> writers = IntStream.range(0, 8).mapToObj(thread -> new Thread(() -> IntStream.range(0, 10_000).forEach(index -> {
            map.put(thread * 10_000 + index, index);
            list.add(index);
        }))).toList();

        writers.forEach(Thread::start);
        for (Thread writer : writers) {
            writer.join();
        }

        assertEquals(80_000, map.size());
        assertEquals(80_000, list.size());
    }

    /** 2026-10-16: a HashMap filled by two regions while the server tick walked it threw ConcurrentModificationException. */
    @Test
    void walkingWhileAnotherThreadWritesNeverThrows() throws InterruptedException {
        Map<Integer, Integer> map = shared(new HashMap<>());
        List<Integer> list = shared(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            started.countDown();
            IntStream.range(0, 200_000).forEach(index -> {
                map.put(index, index);
                list.add(index);
            });
        });
        writer.start();
        TestThreads.await(started);
        while (writer.isAlive()) {
            map.forEach((_, _) -> { });
            map.values().forEach(_ -> { });
            list.forEach(_ -> { });
            list.stream().count();
        }

        writer.join();
        assertEquals(200_000, map.size());
    }

    @Test
    void anIteratorRemovesFromTheLiveCollection() {
        List<Integer> list = shared(new ArrayList<>(List.of(1, 2, 3)));
        Iterator<Integer> walk = list.iterator();
        while (walk.hasNext()) {
            if (walk.next() == 2) {
                walk.remove();
            }
        }

        Map<String, Integer> map = shared(new HashMap<>(Map.of("a", 1, "b", 2)));
        map.entrySet().removeIf(entry -> entry.getValue() == 1);
        map.keySet().iterator().next();

        assertEquals(List.of(1, 3), list);
        assertEquals(Map.of("b", 2), map);
    }

    @Test
    void settingTheValueOfAnEntryWritesTheLiveMap() {
        Map<String, Integer> map = shared(new HashMap<>(Map.of("a", 1)));
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            entry.setValue(5);
        }

        assertEquals(5, map.get("a"));
    }

    @Test
    void aViewStaysLiveAndLocked() {
        List<Integer> list = shared(new ArrayList<>(List.of(1, 2, 3, 4)));
        list.subList(1, 3).clear();
        TreeMap<String, Integer> tree = shared(new TreeMap<>(Map.of("a", 1, "b", 2, "c", 3)));
        tree.headMap("c").remove("a");

        assertEquals(List.of(1, 4), list);
        assertEquals(Map.of("b", 2, "c", 3), tree);
    }

    /** 2026-10-16: exmachina and FramedBlocks fill a set or a map they create inside computeIfAbsent of a shared map, from several regions. */
    @Test
    void computeSharesTheCollectionItCreates() {
        Map<String, Set<Integer>> map = shared(new HashMap<>());
        Set<Integer> created = map.computeIfAbsent("a", _ -> new HashSet<>());
        Long2ObjectOpenHashMap<LongOpenHashSet> chunks = shared(new Long2ObjectOpenHashMap<>());
        LongOpenHashSet positions = chunks.computeIfAbsent(4L, _ -> new LongOpenHashSet());

        assertInstanceOf(SharedCollection.class, created);
        assertSame(created, map.get("a"));
        assertInstanceOf(SharedCollection.class, positions);
        assertSame(positions, chunks.get(4L));
    }

    /** A collection read every tick and rarely written is copied once per write, not once per walk. */
    @Test
    void aWalkReusesTheCopyUntilTheCollectionChanges() {
        Map<String, Integer> map = shared(new HashMap<>(Map.of("a", 1, "b", 2)));
        SharedCollection facade = (SharedCollection) map;
        Object first = facade.leafs$snapshot();
        map.forEach((_, _) -> { });
        map.get("a");
        assertSame(first, facade.leafs$snapshot());

        map.put("c", 3);
        Object afterPut = facade.leafs$snapshot();
        assertTrue(afterPut != first);
        map.keySet().remove("c");
        Object afterView = facade.leafs$snapshot();
        assertTrue(afterView != afterPut);
        Iterator<String> walk = map.keySet().iterator();
        walk.next();
        walk.remove();
        Object afterIterator = facade.leafs$snapshot();
        assertTrue(afterIterator != afterView);
        Map.Entry<String, Integer> entry = map.entrySet().iterator().next();
        entry.setValue(9);

        assertEquals(9, entry.getValue());
        assertEquals(Map.of(entry.getKey(), 9), map);
        assertTrue(facade.leafs$snapshot() != afterIterator);
        assertEquals(1, ((Map<?, ?>) afterIterator).size());
        assertNotEquals(9, ((Map<?, ?>) afterIterator).get(entry.getKey()), "the shared copy is never written");
    }

    @Test
    void forEachCallsTheModOutsideTheMonitor() {
        Map<String, Integer> map = shared(new HashMap<>(Map.of("a", 1)));
        map.forEach((_, _) -> {
            Thread other = new Thread(() -> map.put("b", 2));
            other.start();
            join(other);
        });

        assertEquals(2, map.get("b"));
    }

    private static Object walked(Object collection) {
        if (collection instanceof Map<?, ?> map) {
            Map<Object, Object> walked = new HashMap<>();
            map.forEach(walked::put);
            return walked;
        }

        List<Object> walked = new ArrayList<>();
        ((Collection<?>) collection).forEach(walked::add);
        ((Collection<?>) collection).iterator().forEachRemaining(_ -> { });
        ((Collection<?>) collection).stream().count();
        return walked;
    }

    private static Object contents(Object collection) {
        return collection instanceof Map<?, ?> map ? new HashMap<>(map) : new ArrayList<>((Collection<?>) collection);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static <T> T shared(T collection) {
        Object shared = Shared.wrap(collection);
        assertInstanceOf(collection.getClass(), shared);
        return cast(collection, shared);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(T type, Object shared) {
        return (T) shared;
    }
}
