package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

/**
 * Groups loaded chunks into independently tickable {@link Region}s, one instance per level. Every
 * non-empty section is surrounded by owned buffer sections, so two regions are always separated by at
 * least one full section — that spatial invariant, not locks, is what lets a region touch chunks
 * slightly beyond the ones it owns. Merges touching a ticking region are deferred to its release;
 * splits and dead-section cleanup only happen at release. Pure data structure, no threads, no
 * Minecraft classes. Design reference: {@code docs/sources/folia/Regionizer.md}.
 */
public final class Regionizer<R> {
    private static final int DEAD_SECTION_DIVISOR = 6;

    private final int sectionShift;
    private final int mergeRadius;
    private final int bufferRadius;
    private final int searchRadius;
    private final int connectivityRadius;
    private final int recalcSectionCount;
    private final RegionCallbacks<R> callbacks;

    private final StampedLock lock = new StampedLock();
    private final ConcurrentHashMap<Long, RegionSection<R>> sections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Region<R>> regionsById = new ConcurrentHashMap<>();
    private final AtomicLong nextRegionId = new AtomicLong(1);
    private volatile Thread writeLockOwner;

    public Regionizer(int sectionShift, int mergeRadius, int bufferRadius, RegionCallbacks<R> callbacks) {
        requireRange("sectionShift", sectionShift, 1, 8);
        requireRange("mergeRadius", mergeRadius, 1, 8);
        requireRange("bufferRadius", bufferRadius, 1, 8);
        this.sectionShift = sectionShift;
        this.mergeRadius = mergeRadius;
        this.bufferRadius = bufferRadius;
        this.searchRadius = mergeRadius + bufferRadius;
        this.connectivityRadius = Math.max(mergeRadius, bufferRadius);
        this.recalcSectionCount = Math.max(2, 2048 >> (2 * sectionShift));
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    /** Lock-free bit set while the section stays non-empty; a section becoming non-empty reshapes regions under the write lock. */
    public void addChunk(int chunkX, int chunkZ) {
        long key = CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift);
        RegionSection<R> section = sections.get(key);
        if (section != null && !section.isEmpty()) {
            section.addChunk(chunkX, chunkZ);
            return;
        }

        long stamp = writeLock();
        try {
            addChunkToEmptySection(chunkX, chunkZ, key);
        } finally {
            unlockWrite(stamp);
        }
    }

    /** A section becoming empty marks isolated sections dead; their removal is deferred to the owner's release. */
    public void removeChunk(int chunkX, int chunkZ) {
        long key = CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift);
        RegionSection<R> section = sections.get(key);
        if (section == null) {
            throw new IllegalStateException("Chunk [" + chunkX + ", " + chunkZ + "] has no section to remove from");
        }

        if (section.chunkCount() > 1) {
            section.removeChunk(chunkX, chunkZ);
            return;
        }

        long stamp = writeLock();
        try {
            removeLastChunkOfSection(chunkX, chunkZ, section);
        } finally {
            unlockWrite(stamp);
        }
    }

    /** Owner of the given chunk, or null. Waits out concurrent structural changes. */
    public Region<R> regionAt(int chunkX, int chunkZ) {
        long key = CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift);
        long stamp = lock.tryOptimisticRead();
        RegionSection<R> section = sections.get(key);
        Region<R> region = section == null ? null : section.region();
        if (lock.validate(stamp)) {
            return region;
        }

        stamp = lock.readLock();
        try {
            section = sections.get(key);

            return section == null ? null : section.region();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public int sectionShift() {
        return sectionShift;
    }

    /** Lock-free lookup for threads whose view cannot change, i.e. the thread ticking the owning region. */
    public Region<R> regionAtUnsynchronised(int chunkX, int chunkZ) {
        RegionSection<R> section = sections.get(CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift));

        return section == null ? null : section.region();
    }

    boolean tryMarkTicking(Region<R> region) {
        long stamp = writeLock();
        try {
            if (region.state() != RegionState.READY || !region.mergeIntoLater.isEmpty() || !region.expectingMergeFrom.isEmpty()) {
                return false;
            }

            region.setState(RegionState.TICKING);

            return true;
        } finally {
            unlockWrite(stamp);
        }
    }

    void markNotTicking(Region<R> region) {
        long stamp = writeLock();
        try {
            releaseFromTicking(region);
        } finally {
            unlockWrite(stamp);
        }
    }

    int sectionCountOf(Region<R> region) {
        if (writeLockOwner == Thread.currentThread()) {
            return region.sectionKeys.size();
        }

        long stamp = lock.readLock();
        try {
            return region.sectionKeys.size();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    int chunkCountOf(Region<R> region) {
        if (writeLockOwner == Thread.currentThread()) {
            return sumChunkCounts(region);
        }

        long stamp = lock.readLock();
        try {
            return sumChunkCounts(region);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    private void addChunkToEmptySection(int chunkX, int chunkZ, long key) {
        RegionSection<R> section = sections.get(key);
        List<RegionSection<R>> created = new ArrayList<>();
        if (section == null) {
            section = createSection(key);
            created.add(section);
        }

        int sectionX = CoordinateKey.x(key);
        int sectionZ = CoordinateKey.z(key);
        for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
            for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                long neighbourKey = CoordinateKey.pack(sectionX + dx, sectionZ + dz);
                if (sections.get(neighbourKey) == null) {
                    created.add(createSection(neighbourKey));
                }
            }
        }

        section.addChunk(chunkX, chunkZ);
        for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
            for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                RegionSection<R> neighbour = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                neighbour.gainedNonEmptyNeighbour();
                reviveIfDead(neighbour);
            }
        }
        reviveIfDead(section);

        Collection<Region<R>> nearby = collectNearbyRegions(sectionX, sectionZ);
        Region<R> target = null;
        for (Region<R> candidate : nearby) {
            if (candidate.state() != RegionState.TICKING) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            target = createRegion();
        }

        for (RegionSection<R> freshSection : created) {
            adopt(target, freshSection);
        }
        for (Region<R> other : nearby) {
            if (other != target) {
                linkDeferredMerge(other, target);
            }
        }
        resolvePendingMerges(target);
    }

    private void removeLastChunkOfSection(int chunkX, int chunkZ, RegionSection<R> section) {
        section.removeChunk(chunkX, chunkZ);
        if (!section.isEmpty()) {
            throw new IllegalStateException("Section " + CoordinateKey.x(section.key()) + ", " + CoordinateKey.z(section.key()) + " was mutated concurrently during removal");
        }

        int sectionX = CoordinateKey.x(section.key());
        int sectionZ = CoordinateKey.z(section.key());
        for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
            for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                RegionSection<R> neighbour = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                if (neighbour == null) {
                    throw new IllegalStateException("Buffer section missing around non-empty section [" + sectionX + ", " + sectionZ + "]");
                }

                neighbour.lostNonEmptyNeighbour();
                markDeadIfIsolated(neighbour);
            }
        }
        markDeadIfIsolated(section);
    }

    private void releaseFromTicking(Region<R> region) {
        if (region.state() != RegionState.TICKING) {
            throw new IllegalStateException(region + " released without being marked ticking");
        }

        region.setState(RegionState.READY);

        if (resolvePendingMerges(region) != region) {
            return;
        }

        if (!region.mergeIntoLater.isEmpty()) {
            region.setState(RegionState.TRANSIENT);
            callbacks.onRegionInactive(region);
            return;
        }

        if (!region.expectingMergeFrom.isEmpty()) {
            return;
        }

        int total = region.sectionKeys.size();
        int dead = region.deadSectionKeys.size();
        if (dead == 0) {
            return;
        }

        boolean allDead = dead == total;
        if (!allDead && (total < recalcSectionCount || dead * DEAD_SECTION_DIVISOR < total)) {
            return;
        }

        removeDeadSections(region);
        if (region.sectionKeys.isEmpty()) {
            destroyEmptiedRegion(region);
            return;
        }

        List<LongOpenHashSet> components = connectedComponents(region);
        if (components.size() > 1) {
            splitRegion(region, components);
        }
    }

    /**
     * Executes every pending merge around {@code region} whose two sides are not ticking, following
     * the surviving region as merges chain. Without this, link forwarding could leave two non-ticking
     * regions owing each other a merge that the ticking gate would then block forever.
     */
    private Region<R> resolvePendingMerges(Region<R> region) {
        boolean progress = true;
        while (progress && region.state() != RegionState.DEAD) {
            progress = false;
            for (Region<R> source : List.copyOf(region.expectingMergeFrom)) {
                if (source.state() != RegionState.TICKING) {
                    killAndMergeInto(source, region);
                    progress = true;
                }
            }
            for (Region<R> target : List.copyOf(region.mergeIntoLater)) {
                if (target.state() != RegionState.TICKING) {
                    killAndMergeInto(region, target);
                    region = target;
                    progress = true;
                    break;
                }
            }
        }

        return region;
    }

    private void killAndMergeInto(Region<R> from, Region<R> into) {
        if (from == into || from.state() == RegionState.TICKING || into.state() == RegionState.TICKING) {
            throw new IllegalStateException("Illegal merge of " + from + " into " + into);
        }

        boolean fromWasSchedulable = from.state() == RegionState.READY;
        from.setState(RegionState.DEAD);
        from.mergeIntoLater.remove(into);
        into.expectingMergeFrom.remove(from);

        for (LongIterator iterator = from.sectionKeys.iterator(); iterator.hasNext(); ) {
            long key = iterator.nextLong();
            sections.get(key).setRegion(into);
            into.sectionKeys.add(key);
        }
        into.deadSectionKeys.addAll(from.deadSectionKeys);

        for (Region<R> forwardedTarget : from.mergeIntoLater) {
            forwardedTarget.expectingMergeFrom.remove(from);
            if (forwardedTarget != into) {
                linkDeferredMerge(into, forwardedTarget);
            }
        }
        for (Region<R> forwardedSource : from.expectingMergeFrom) {
            forwardedSource.mergeIntoLater.remove(from);
            if (forwardedSource != into) {
                forwardedSource.mergeIntoLater.add(into);
                into.expectingMergeFrom.add(forwardedSource);
            }
        }
        from.sectionKeys.clear();
        from.deadSectionKeys.clear();
        from.mergeIntoLater.clear();
        from.expectingMergeFrom.clear();

        if (fromWasSchedulable) {
            callbacks.onRegionInactive(from);
        }
        callbacks.merge(from, into);
        regionsById.remove(from.id());
        callbacks.onRegionDestroy(from);
    }

    private void splitRegion(Region<R> parent, List<LongOpenHashSet> components) {
        parent.setState(RegionState.DEAD);
        callbacks.onRegionInactive(parent);

        List<Region<R>> children = new ArrayList<>(components.size());
        Long2ObjectMap<Region<R>> sectionToChild = new Long2ObjectOpenHashMap<>(parent.sectionKeys.size());
        for (LongOpenHashSet component : components) {
            Region<R> child = new Region<>(nextRegionId.getAndIncrement(), this, callbacks);
            regionsById.put(child.id(), child);
            callbacks.onRegionCreate(child);
            for (LongIterator iterator = component.iterator(); iterator.hasNext(); ) {
                long key = iterator.nextLong();
                adopt(child, sections.get(key));
                sectionToChild.put(key, child);
            }
            children.add(child);
        }
        parent.sectionKeys.clear();

        callbacks.split(parent, sectionToChild, List.copyOf(children));
        regionsById.remove(parent.id());
        callbacks.onRegionDestroy(parent);
        for (Region<R> child : children) {
            callbacks.onRegionActive(child);
        }
    }

    private void destroyEmptiedRegion(Region<R> region) {
        region.setState(RegionState.DEAD);
        callbacks.onRegionInactive(region);
        regionsById.remove(region.id());
        callbacks.onRegionDestroy(region);
    }

    private void removeDeadSections(Region<R> region) {
        for (LongIterator iterator = region.deadSectionKeys.iterator(); iterator.hasNext(); ) {
            long key = iterator.nextLong();
            RegionSection<R> removed = sections.remove(key);
            if (removed == null || !removed.isEmpty() || removed.nonEmptyNeighbours() > 0) {
                throw new IllegalStateException("Section " + CoordinateKey.x(key) + ", " + CoordinateKey.z(key) + " was marked dead but is still alive");
            }

            removed.clearRegion();
            region.sectionKeys.remove(key);
        }
        region.deadSectionKeys.clear();
    }

    private List<LongOpenHashSet> connectedComponents(Region<R> region) {
        List<LongOpenHashSet> components = new ArrayList<>();
        LongOpenHashSet remaining = new LongOpenHashSet(region.sectionKeys);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().nextLong();
            remaining.remove(seed);
            LongOpenHashSet component = new LongOpenHashSet();
            component.add(seed);
            queue.enqueue(seed);
            while (!queue.isEmpty()) {
                long current = queue.dequeueLong();
                int currentX = CoordinateKey.x(current);
                int currentZ = CoordinateKey.z(current);
                for (int dx = -connectivityRadius; dx <= connectivityRadius; dx++) {
                    for (int dz = -connectivityRadius; dz <= connectivityRadius; dz++) {
                        long neighbourKey = CoordinateKey.pack(currentX + dx, currentZ + dz);
                        if (remaining.remove(neighbourKey)) {
                            component.add(neighbourKey);
                            queue.enqueue(neighbourKey);
                        }
                    }
                }
            }
            components.add(component);
        }

        return components;
    }

    private Collection<Region<R>> collectNearbyRegions(int sectionX, int sectionZ) {
        Collection<Region<R>> nearby = new LinkedHashSet<>();
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                RegionSection<R> section = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                if (section != null && section.region() != null) {
                    nearby.add(section.region());
                }
            }
        }

        return nearby;
    }

    private RegionSection<R> createSection(long key) {
        RegionSection<R> section = new RegionSection<>(key, sectionShift);
        int sectionX = CoordinateKey.x(key);
        int sectionZ = CoordinateKey.z(key);
        int nonEmptyNeighbours = 0;
        for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
            for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                RegionSection<R> neighbour = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                if (neighbour != null && !neighbour.isEmpty()) {
                    nonEmptyNeighbours++;
                }
            }
        }
        section.initialiseNonEmptyNeighbours(nonEmptyNeighbours);
        sections.put(key, section);

        return section;
    }

    private Region<R> createRegion() {
        Region<R> region = new Region<>(nextRegionId.getAndIncrement(), this, callbacks);
        regionsById.put(region.id(), region);
        callbacks.onRegionCreate(region);
        callbacks.onRegionActive(region);

        return region;
    }

    private void adopt(Region<R> region, RegionSection<R> section) {
        section.setRegion(region);
        region.sectionKeys.add(section.key());
    }

    private void linkDeferredMerge(Region<R> from, Region<R> into) {
        if (from.mergeIntoLater.add(into)) {
            into.expectingMergeFrom.add(from);
        }
    }

    private void reviveIfDead(RegionSection<R> section) {
        Region<R> owner = section.region();
        if (owner != null) {
            owner.deadSectionKeys.remove(section.key());
        }
    }

    private void markDeadIfIsolated(RegionSection<R> section) {
        if (!section.isEmpty() || section.nonEmptyNeighbours() > 0) {
            return;
        }

        Region<R> owner = section.region();
        if (owner == null) {
            throw new IllegalStateException("Section " + CoordinateKey.x(section.key()) + ", " + CoordinateKey.z(section.key()) + " has no owning region");
        }

        owner.deadSectionKeys.add(section.key());
    }

    private int sumChunkCounts(Region<R> region) {
        int total = 0;
        for (LongIterator iterator = region.sectionKeys.iterator(); iterator.hasNext(); ) {
            total += sections.get(iterator.nextLong()).chunkCount();
        }

        return total;
    }

    private long writeLock() {
        if (writeLockOwner == Thread.currentThread()) {
            throw new IllegalStateException("Regionizer lock re-entered — callbacks must not call back into the regionizer");
        }

        long stamp = lock.writeLock();
        writeLockOwner = Thread.currentThread();

        return stamp;
    }

    private void unlockWrite(long stamp) {
        writeLockOwner = null;
        lock.unlockWrite(stamp);
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "], got " + value);
        }
    }

    Map<Long, RegionSection<R>> sectionsView() {
        return sections;
    }

    Collection<Region<R>> regionsView() {
        return regionsById.values();
    }

    int mergeRadiusValue() {
        return mergeRadius;
    }

    int bufferRadiusValue() {
        return bufferRadius;
    }
}
