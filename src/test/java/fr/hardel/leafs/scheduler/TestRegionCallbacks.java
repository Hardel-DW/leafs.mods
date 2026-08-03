package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionCallbacks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.List;

/** Wires the task queues into region merge/split - the same wiring ticking/ will do for real. */
final class TestRegionCallbacks implements RegionCallbacks<TestRegionData> {
    private final int sectionShift;

    TestRegionCallbacks(int sectionShift) {
        this.sectionShift = sectionShift;
    }

    @Override
    public TestRegionData createData(Region<TestRegionData> region) {
        return new TestRegionData();
    }

    @Override
    public void onRegionCreate(Region<TestRegionData> region) {
    }

    @Override
    public void onRegionDestroy(Region<TestRegionData> region) {
    }

    @Override
    public void onRegionActive(Region<TestRegionData> region) {
    }

    @Override
    public void onRegionInactive(Region<TestRegionData> region) {
    }

    @Override
    public void merge(Region<TestRegionData> from, Region<TestRegionData> into) {
        from.data().taskQueues().closeInto(into.data().taskQueues());
    }

    @Override
    public void split(Region<TestRegionData> parent, Long2ObjectMap<Region<TestRegionData>> sectionToChild, List<Region<TestRegionData>> children) {
        parent.data().taskQueues().closeAndReroute(sectionShift, sectionKey -> {
            Region<TestRegionData> child = sectionToChild.get(sectionKey);

            return child == null ? null : child.data().taskQueues();
        });
    }
}
