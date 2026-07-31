package fr.hardel.leafs.scheduler;

final class TestRegionData implements RegionTaskHost {
    private final RegionTaskQueues taskQueues = new RegionTaskQueues();

    @Override
    public RegionTaskQueues taskQueues() {
        return taskQueues;
    }
}
