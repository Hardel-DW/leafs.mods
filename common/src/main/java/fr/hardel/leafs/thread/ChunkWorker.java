package fr.hardel.leafs.thread;

public final class ChunkWorker extends LeafsThread {
    private int heldChunks;

    public ChunkWorker(ThreadGroup serverThreads, Runnable work, int index) {
        super(serverThreads, work, "Chunk Worker", index);
    }

    void hold() {
        heldChunks++;
    }

    void release() {
        heldChunks--;
    }

    @Override
    boolean runsGame() {
        return heldChunks > 0;
    }
}
