package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.ArrayList;
import java.util.List;

/** Records every callback invocation as a readable event line, in order. */
class RecordingCallbacks implements RegionCallbacks<Object> {
    final List<String> events = new ArrayList<>();

    @Override
    public Object createData(Region<Object> region) {
        events.add("data #" + region.id());

        return new Object();
    }

    @Override
    public void onRegionCreate(Region<Object> region) {
        events.add("create #" + region.id());
    }

    @Override
    public void onRegionDestroy(Region<Object> region) {
        events.add("destroy #" + region.id());
    }

    @Override
    public void onRegionActive(Region<Object> region) {
        events.add("active #" + region.id());
    }

    @Override
    public void onRegionInactive(Region<Object> region) {
        events.add("inactive #" + region.id());
    }

    @Override
    public void merge(Region<Object> from, Region<Object> into) {
        events.add("merge #" + from.id() + "->#" + into.id());
    }

    @Override
    public void split(Region<Object> parent, Long2ObjectMap<Region<Object>> sectionToChild, List<Region<Object>> children) {
        List<Long> childIds = children.stream().map(Region::id).sorted().toList();
        events.add("split #" + parent.id() + "->" + childIds);
    }
}
