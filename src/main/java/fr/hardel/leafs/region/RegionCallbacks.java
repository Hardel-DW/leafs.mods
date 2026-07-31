package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.List;

/**
 * Hooks other modules implement to follow the region lifecycle and partition their state on merge
 * and split. Every callback runs under the regionizer's critical lock: implementations must be
 * non-blocking, must not touch world state and must never call back into the regionizer.
 *
 * <p>Lifecycle order guarantees:
 * <ul>
 *   <li>Creation: {@code createData} → {@code onRegionCreate} → {@code onRegionActive}.</li>
 *   <li>Merge (the losing region): {@code onRegionInactive} (only if it was {@link RegionState#READY})
 *       → {@code merge} → {@code onRegionDestroy}.</li>
 *   <li>Split: {@code onRegionInactive(parent)} → per child {@code createData} + {@code onRegionCreate}
 *       → {@code split} → {@code onRegionDestroy(parent)} → per child {@code onRegionActive}.</li>
 *   <li>A region released with a pending merge whose target is ticking goes
 *       {@link RegionState#TRANSIENT} and fires {@code onRegionInactive} once.</li>
 * </ul>
 */
public interface RegionCallbacks<R> {

    /** Called from the region constructor: the id is set, the sections are not yet assigned. */
    R createData(Region<R> region);

    void onRegionCreate(Region<R> region);

    void onRegionDestroy(Region<R> region);

    /** The tick scheduler should start attempting to tick this region. */
    void onRegionActive(Region<R> region);

    /** The tick scheduler must forget this region's handle. */
    void onRegionInactive(Region<R> region);

    /**
     * Folds {@code from}'s data into {@code into}'s. {@code from} is already dead and its sections
     * already belong to {@code into}; time-based state is rebased by the implementor (two clocks).
     */
    void merge(Region<R> from, Region<R> into);

    /** Sections are already reassigned: {@code sectionToChild} re-buckets position-keyed state. */
    void split(Region<R> parent, Long2ObjectMap<Region<R>> sectionToChild, List<Region<R>> children);
}
