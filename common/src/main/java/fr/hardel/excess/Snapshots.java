package fr.hardel.excess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

final class Snapshots {

    private Snapshots() {
    }

    static <E> boolean removeIf(Collection<E> live, List<E> snapshot, Predicate<? super E> filter) {
        List<E> matched = new ArrayList<>();
        for (E element : snapshot) {
            if (filter.test(element)) {
                matched.add(element);
            }
        }

        return !matched.isEmpty() && live.removeAll(matched);
    }
}
