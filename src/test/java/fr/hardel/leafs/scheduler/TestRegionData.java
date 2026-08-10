package fr.hardel.leafs.scheduler;

final class TestRegionData implements RegionTaskHost {
    private final RegionTaskQueues taskQueues = new RegionTaskQueues();
    private final RegionTaskQueues unloadQueues = new RegionTaskQueues();

    @Override
    public RegionTaskQueues taskQueues() {
        return taskQueues;
    }

    @Override
    public RegionTaskQueues unloadQueues() {
        return unloadQueues;
    }
}
