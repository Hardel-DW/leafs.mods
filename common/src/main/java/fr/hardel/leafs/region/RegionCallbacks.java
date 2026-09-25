package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.List;

public interface RegionCallbacks<R> {

    R createData(Region<R> region);

    void onRegionCreate(Region<R> region);

    void onRegionDestroy(Region<R> region);

    void onRegionActive(Region<R> region);

    void onRegionInactive(Region<R> region);

    void merge(Region<R> from, Region<R> into, LongList movedChunks);

    void split(Region<R> parent, Long2ObjectMap<Region<R>> sectionToChild, List<Region<R>> children);
}
