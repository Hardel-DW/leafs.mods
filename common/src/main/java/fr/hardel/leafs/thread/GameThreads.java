package fr.hardel.leafs.thread;

public final class GameThreads {
    private static volatile Thread server;

    private GameThreads() {
    }

    public static void bindServer() {
        server = Thread.currentThread();
    }

    public static boolean isServer() {
        return Thread.currentThread() == server;
    }

    public static boolean isGame() {
        return isServer() || isGameWorker();
    }

    public static boolean isGameWorker() {
        return Thread.currentThread() instanceof LeafsThread worker && worker.runsGame();
    }

    public static boolean isRegionWorker() {
        return Thread.currentThread() instanceof RegionWorker;
    }

    public static boolean isChunkWorker() {
        return Thread.currentThread() instanceof ChunkWorker;
    }

    public static boolean sameThreadAs(Thread loop) {
        return loop == server ? isGame() : Thread.currentThread() == loop;
    }

    public static void heldChunk() {
        if (Thread.currentThread() instanceof ChunkWorker worker) {
            worker.hold();
        }
    }

    public static void releasedChunk() {
        if (Thread.currentThread() instanceof ChunkWorker worker) {
            worker.release();
        }
    }
}
