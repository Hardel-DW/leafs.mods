package fr.hardel.leafs.region;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntSupplier;

public final class Regionizer<R> {
    private static final int DEAD_SECTION_DIVISOR = 6;

    private final int sectionShift;
    final int mergeRadius;
    final int bufferRadius;
    private final int searchRadius;
    private final int connectivityRadius;
    private final RegionCallbacks<R> callbacks;

    private final StampedLock lock = new StampedLock();
    final ConcurrentLong2ObjectMap<RegionSection<R>> sections = new ConcurrentLong2ObjectMap<>();
    private final ConcurrentLong2ObjectMap<Region<R>> regionsById = new ConcurrentLong2ObjectMap<>();
    private final Collection<Region<R>> regionsView = Collections.unmodifiableCollection(regionsById.values());
    private final AtomicLong nextRegionId = new AtomicLong(1);

    public Regionizer(int sectionShift, int mergeRadius, int bufferRadius, RegionCallbacks<R> callbacks) {
        this.sectionShift = sectionShift;
        this.mergeRadius = mergeRadius;
        this.bufferRadius = bufferRadius;
        this.searchRadius = mergeRadius + bufferRadius;
        this.connectivityRadius = Math.max(mergeRadius, bufferRadius);
        this.callbacks = callbacks;
    }

    public void addChunk(int chunkX, int chunkZ) {
        long key = CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift);
        RegionSection<R> section = sections.get(key);
        if (section != null && !section.isEmpty()) {
            section.addChunk();
            return;
        }

        long stamp = lock.writeLock();
        try {
            addChunkToEmptySection(key);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public void removeChunk(int chunkX, int chunkZ) {
        long key = CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift);
        RegionSection<R> section = sections.get(key);
        if (section.chunkCount() > 1) {
            section.removeChunk();
            return;
        }

        long stamp = lock.writeLock();
        try {
            removeLastChunkOfSection(section);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // Used by the Leafs Debug mod
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

    // Used by the Leafs Debug mod
    public int sectionShift() {
        return sectionShift;
    }

    public Region<R> regionAtUnsynchronised(int chunkX, int chunkZ) {
        RegionSection<R> section = sections.get(CoordinateKey.pack(chunkX >> sectionShift, chunkZ >> sectionShift));

        return section == null ? null : section.region();
    }

    // Used by the Leafs Debug mod
    public Collection<Region<R>> regionsView() {
        return regionsView;
    }

    boolean tryMarkTicking(Region<R> region, boolean despiteMerges) {
        long stamp = lock.writeLock();
        try {
            RegionState state = region.state();
            if (state == RegionState.TICKING || state == RegionState.DEAD) {
                return false;
            }

            if (!despiteMerges && (!region.mergeIntoLater.isEmpty() || !region.expectingMergeFrom.isEmpty())) {
                return false;
            }

            region.setState(RegionState.TICKING);

            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    void markNotTicking(Region<R> region) {
        long stamp = lock.writeLock();
        try {
            releaseFromTicking(region);
        } finally {
            lock.unlockWrite(stamp);
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

    long[] sectionKeysOf(Region<R> region) {
        long stamp = lock.readLock();
        try {
            return region.sectionKeys.toLongArray();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    private int readCount(IntSupplier count) {
        long stamp = lock.readLock();
        try {
            return count.getAsInt();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    private void addChunkToEmptySection(long key) {
        RegionSection<R> section = sections.get(key);
        List<RegionSection<R>> created = new ArrayList<>();
        if (section == null) {
            section = createSection(key);
            created.add(section);
        }

        section.addChunk();
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

    private void removeLastChunkOfSection(RegionSection<R> section) {
        section.removeChunk();
        int sectionX = CoordinateKey.x(section.key());
        int sectionZ = CoordinateKey.z(section.key());
        for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
            for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                RegionSection<R> neighbour = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                neighbour.lostNonEmptyNeighbour();
                markDeadIfIsolated(neighbour);
            }
        }
        markDeadIfIsolated(section);
    }

    private void releaseFromTicking(Region<R> region) {
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

        if (dead * DEAD_SECTION_DIVISOR < total) {
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
        boolean fromWasSchedulable = from.state() == RegionState.READY;
        from.setState(RegionState.DEAD);
        from.mergeIntoLater.remove(into);
        into.expectingMergeFrom.remove(from);

        LongList movedChunks = new LongArrayList();
        for (LongIterator iterator = from.sectionKeys.iterator(); iterator.hasNext(); ) {
            long key = iterator.nextLong();
            RegionSection<R> section = sections.get(key);
            section.forEachChunkKey(movedChunks::add);
            section.setRegion(into);
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
        callbacks.merge(from, into, movedChunks);
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
            sections.remove(key).clearRegion();
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

        section.region().deadSectionKeys.add(section.key());
    }

    private int sumChunkCounts(Region<R> region) {
        int total = 0;
        for (LongIterator iterator = region.sectionKeys.iterator(); iterator.hasNext(); ) {
            total += sections.get(iterator.nextLong()).chunkCount();
        }

        return total;
    }
}
