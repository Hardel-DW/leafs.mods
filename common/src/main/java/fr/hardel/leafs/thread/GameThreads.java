package fr.hardel.leafs.thread;

import java.util.function.IntSupplier;

public final class GameThreads {
    private static volatile Thread server;
    private static volatile IntSupplier serverTicks = () -> 0;

    private GameThreads() {
    }

    public static void bindServer(IntSupplier tickCount) {
        serverTicks = tickCount;
        server = Thread.currentThread();
    }

    public static void beginWork() {
        if (Thread.currentThread() instanceof LeafsThread worker) {
            worker.beginWork(serverTicks.getAsInt());
        }
    }

    public static int tickCount(int serverTick) {
        return Thread.currentThread() instanceof LeafsThread worker && worker.runsGame() ? worker.workTick() : serverTick;
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
            if (!worker.runsGame()) {
                worker.beginWork(serverTicks.getAsInt());
            }

            worker.hold();
        }
    }

    public static void releasedChunk() {
        if (Thread.currentThread() instanceof ChunkWorker worker) {
            worker.release();
        }
    }
}
