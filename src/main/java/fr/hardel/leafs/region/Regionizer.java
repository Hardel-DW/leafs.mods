package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntSupplier;

/** Groups loaded chunks into {@link Region}s. Non-empty sections carry a buffer ring, so two regions stay one full section apart. */
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
    private final Collection<Region<R>> regionsView = Collections.unmodifiableCollection(regionsById.values());
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

    /** Falls back to a full read lock if a concurrent structural change invalidates the optimistic read. */
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

    /** Live, read-only view: iteration is weakly consistent, so a region read from it may already be dead. */
    public Collection<Region<R>> regionsView() {
        return regionsView;
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
        return readCount(() -> region.sectionKeys.size());
    }

    int deadSectionCountOf(Region<R> region) {
        return readCount(() -> region.deadSectionKeys.size());
    }

    int chunkCountOf(Region<R> region) {
        return readCount(() -> sumChunkCounts(region));
    }

    /** Snapshot under the read lock: the feed grows a ticking region's sections, so the owner never iterates the live set. */
    long[] sectionKeysOf(Region<R> region) {
        if (writeLockOwner == Thread.currentThread()) {
            return region.sectionKeys.toLongArray();
        }

        long stamp = lock.readLock();
        try {
            return region.sectionKeys.toLongArray();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /** The bypass is what lets a callback read a count: taking the read lock while owning the write lock deadlocks a {@link StampedLock}. */
    private int readCount(IntSupplier count) {
        if (writeLockOwner == Thread.currentThread()) {
            return count.getAsInt();
        }

        long stamp = lock.readLock();
        try {
            return count.getAsInt();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /** The section is non-empty before the ring walk: a buffer section created here already counts it. */
    private void addChunkToEmptySection(int chunkX, int chunkZ, long key) {
        RegionSection<R> section = sections.get(key);
        List<RegionSection<R>> created = new ArrayList<>();
        if (section == null) {
            section = createSection(key);
            created.add(section);
        }

        section.addChunk(chunkX, chunkZ);
        reviveIfDead(section);

        int sectionX = CoordinateKey.x(key);
        int sectionZ = CoordinateKey.z(key);
        for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
            for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                long neighbourKey = CoordinateKey.pack(sectionX + dx, sectionZ + dz);
                RegionSection<R> neighbour = sections.get(neighbourKey);
                if (neighbour == null) {
                    created.add(createSection(neighbourKey));
                } else {
                    neighbour.gainedNonEmptyNeighbour();
                    reviveIfDead(neighbour);
                }
            }
        }

        Collection<Region<R>> nearby = collectNearbyRegions(sectionX, sectionZ);
        Region<R> target = preferredTarget(nearby);
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
            throw new IllegalStateException("Section " + CoordinateKey.describe(section.key()) + " was mutated concurrently during removal");
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
                    throw new IllegalStateException("Buffer section missing around non-empty section " + CoordinateKey.describe(section.key()));
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

    /** Runs every pending merge around {@code region} whose two sides are idle, following the survivor as merges chain. */
    private Region<R> resolvePendingMerges(Region<R> region) {
        boolean progress = true;
        while (progress && region.state() != RegionState.DEAD && region.state() != RegionState.TICKING) {
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
                throw new IllegalStateException("Section " + CoordinateKey.describe(key) + " was marked dead but is still alive");
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

        RegionSection<R> section = new RegionSection<>(key, sectionShift, nonEmptyNeighbours);
        sections.put(key, section);

        return section;
    }

    /** A ticking neighbour is a legal target (sections only ever arrive); an idle one is preferred. */
    private Region<R> preferredTarget(Collection<Region<R>> nearby) {
        Region<R> chosen = null;
        for (Region<R> candidate : nearby) {
            if (chosen == null || (chosen.state() == RegionState.TICKING && candidate.state() != RegionState.TICKING)) {
                chosen = candidate;
            }
        }

        return chosen;
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
            throw new IllegalStateException("Section " + CoordinateKey.describe(section.key()) + " has no owning region");
        }

        owner.deadSectionKeys.add(section.key());
    }

    /** Walks a snapshot: the feed adopts sections into a region while it ticks, and a dead section may vanish under another region's release. */
    void forEachChunkOf(Region<R> region, Region.ChunkConsumer consumer) {
        for (long key : sectionKeysOf(region)) {
            RegionSection<R> section = sections.get(key);
            if (section != null) {
                section.forEachChunk(consumer);
            }
        }
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
            throw new IllegalStateException("Regionizer lock re-entered - callbacks must not call back into the regionizer");
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

    int mergeRadiusValue() {
        return mergeRadius;
    }

    int bufferRadiusValue() {
        return bufferRadius;
    }
}
