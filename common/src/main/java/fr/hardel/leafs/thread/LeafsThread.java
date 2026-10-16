package fr.hardel.leafs.thread;

public abstract sealed class LeafsThread extends Thread permits RegionWorker, ChunkWorker {
    private int workTick;

    LeafsThread(ThreadGroup serverThreads, Runnable body, String role, int index) {
        super(serverThreads, body, "Leafs Server %s #%s".formatted(role, index));
        setDaemon(true);
    }

    abstract boolean runsGame();

    void beginWork(int serverTick) {
        workTick = serverTick;
    }

    int workTick() {
        return workTick;
    }
}
