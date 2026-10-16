package fr.hardel.leafs.thread;

public final class RegionWorker extends LeafsThread {
    public RegionWorker(ThreadGroup serverThreads, Runnable loop, int index) {
        super(serverThreads, loop, "Region Worker", index);
    }

    @Override
    boolean runsGame() {
        return true;
    }
}
