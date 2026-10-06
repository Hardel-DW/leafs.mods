package fr.hardel.leafs.region;

import fr.hardel.excess.ConcurrentLongSet;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * The sections whose chunks changed since their region last read them. Whoever changes a chunk marks its section, once the change is visible. A section no
 * region owns is not marked: the region that takes it later reads it whole.
 */
public final class SectionChanges {
    private final ConcurrentLongSet marked = new ConcurrentLongSet();
    private final int sectionShift;
    private final LongPredicate owned;

    public SectionChanges(int sectionShift, LongPredicate owned) {
        this.sectionShift = sectionShift;
        this.owned = owned;
    }

    public void mark(int chunkX, int chunkZ) {
        markSection(CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift));
    }

    public void markSection(long sectionKey) {
        if (owned.test(sectionKey)) {
            marked.add(sectionKey);
        }
    }

    /** A region took the section: it reads it whole. */
    public void assigned(long sectionKey) {
        marked.add(sectionKey);
    }

    /** No region owns the section any more: nobody would read its mark. */
    public void released(long sectionKey) {
        marked.remove(sectionKey);
    }

    /** Hands over the marked sections the caller answers for. A change that lands meanwhile marks its section again. */
    public void take(LongPredicate mine, LongConsumer section) {
        for (long key : marked.toLongArray()) {
            if (mine.test(key) && marked.remove(key)) {
                section.accept(key);
            }
        }
    }
}
