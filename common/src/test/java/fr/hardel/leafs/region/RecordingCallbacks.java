package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.ArrayList;
import java.util.List;

class RecordingCallbacks implements RegionCallbacks<Object> {
    final List<String> events = new ArrayList<>();

    @Override
    public Object createData(Region<Object> region) {
        events.add("data #%s".formatted(region.id()));

        return new Object();
    }

    @Override
    public void onRegionCreate(Region<Object> region) {
        events.add("create #%s".formatted(region.id()));
    }

    @Override
    public void onRegionDestroy(Region<Object> region) {
        events.add("destroy #%s".formatted(region.id()));
    }

    @Override
    public void onRegionActive(Region<Object> region) {
        events.add("active #%s".formatted(region.id()));
    }

    @Override
    public void onRegionInactive(Region<Object> region) {
        events.add("inactive #%s".formatted(region.id()));
    }

    @Override
    public void merge(Region<Object> from, Region<Object> into, LongList movedChunks) {
        events.add("merge #%s->#%s".formatted(from.id(), into.id()));
    }

    @Override
    public void split(Region<Object> parent, Long2ObjectMap<Region<Object>> sectionToChild, List<Region<Object>> children) {
        List<Long> childIds = children.stream().map(Region::id).sorted().toList();
        events.add("split #%s->%s".formatted(parent.id(), childIds));
    }
}
