package fr.hardel.excess;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class SynchronizedLongOpenHashSet extends LongOpenHashSet {

    @Override
    public synchronized boolean add(long value) {
        return super.add(value);
    }
}
