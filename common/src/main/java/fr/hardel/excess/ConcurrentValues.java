package fr.hardel.excess;

import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ConcurrentHashMap;

final class ConcurrentValues<V> extends AbstractObjectCollection<V> {
    private final ConcurrentHashMap<?, V> map;

    ConcurrentValues(ConcurrentHashMap<?, V> map) {
        this.map = map;
    }

    @Override
    public @NonNull ObjectIterator<V> iterator() {
        return ObjectIterators.asObjectIterator(map.values().iterator());
    }

    @Override
    public @NonNull ObjectSpliterator<V> spliterator() {
        return ObjectSpliterators.asSpliteratorUnknownSize(iterator(), 0);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean contains(Object value) {
        return map.containsValue(value);
    }

    @Override
    public void clear() {
        map.clear();
    }
}
