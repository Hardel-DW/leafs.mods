package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** LongSet over a ConcurrentHashMap key set: atomic membership, weakly consistent iteration, boxing accepted. */
public final class ConcurrentLongSet extends AbstractLongSet {
    private final Set<Long> set = ConcurrentHashMap.newKeySet();

    @Override
    public boolean add(long value) {
        return set.add(value);
    }

    @Override
    public boolean remove(long value) {
        return set.remove(value);
    }

    @Override
    public boolean contains(long value) {
        return set.contains(value);
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public @NonNull LongIterator iterator() {
        Iterator<Long> backing = set.iterator();
        return new LongIterator() {
            @Override
            public boolean hasNext() {
                return backing.hasNext();
            }

            @Override
            public long nextLong() {
                return backing.next();
            }

            @Override
            public void remove() {
                backing.remove();
            }
        };
    }
}
