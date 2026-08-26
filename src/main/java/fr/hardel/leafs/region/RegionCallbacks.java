package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.List;

/** Region lifecycle hooks. Every callback runs under the regionizer's write lock: no blocking, no world state, no call back into the regionizer. */
public interface RegionCallbacks<R> {

    /** Called from the region constructor: the id is set, the sections are not yet assigned. */
    R createData(Region<R> region);

    void onRegionCreate(Region<R> region);

    void onRegionDestroy(Region<R> region);

    void onRegionActive(Region<R> region);

    void onRegionInactive(Region<R> region);

    /** {@code from} is already dead and its sections belong to {@code into}; clocks are rebased by the implementor. */
    void merge(Region<R> from, Region<R> into);

    /** Sections are already reassigned: {@code sectionToChild} re-buckets position-keyed state. */
    void split(Region<R> parent, Long2ObjectMap<Region<R>> sectionToChild, List<Region<R>> children);
}
