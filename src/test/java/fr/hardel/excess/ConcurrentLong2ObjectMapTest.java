package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The keys are spread before the backing map and folded back on every read path, so a packed coordinate comes out as it went in. */
class ConcurrentLong2ObjectMapTest {
    private static final long[] KEYS = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, (7L << 32) | 3L, (-12L << 32) | (45L & 0xFFFFFFFFL)};

    @Test
    void keysRoundTripThroughEveryView() {
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>();
        for (long key : KEYS) {
            map.put(key, Long.toString(key));
        }

        LongOpenHashSet seen = new LongOpenHashSet();
        for (Long2ObjectMap.Entry<String> entry : map.long2ObjectEntrySet()) {
            assertEquals(Long.toString(entry.getLongKey()), entry.getValue());
            seen.add(entry.getLongKey());
        }

        assertEquals(KEYS.length, seen.size());
        assertEquals(seen, map.keySet());
        for (long key : KEYS) {
            assertTrue(map.containsKey(key));
            assertEquals(Long.toString(key), map.get(key));
        }
    }

    @Test
    void computeSeesTheUnmixedKey() {
        ConcurrentLong2ObjectMap<Long> map = new ConcurrentLong2ObjectMap<>();
        long key = (5L << 32) | 9L;
        map.compute(key, (seen, _) -> seen);
        assertEquals(key, map.get(key));
        map.compute(key, (_, _) -> null);
        assertNull(map.get(key));
    }

    @Test
    void setIteratesTheValuesItWasGiven() {
        ConcurrentLongSet set = new ConcurrentLongSet();
        for (long key : KEYS) {
            set.add(key);
        }

        LongOpenHashSet seen = new LongOpenHashSet(set);
        assertEquals(new LongOpenHashSet(KEYS), seen);
    }
}
