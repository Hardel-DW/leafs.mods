package fr.hardel.leafs.chunk.propagator;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/** Lock over rectangular cell areas, owned by the node it hands out, so any thread may release it. A conflict rolls back fully before parking, so two areas never deadlock. */
public final class AreaLock {

    private final int shift;
    private final ConcurrentLong2ObjectMap<Node> cells = new ConcurrentLong2ObjectMap<>();

    public AreaLock(int shift) {
        this.shift = shift;
    }

    public Node lock(int x, int z, int radius) {
        return lock(x - radius, z - radius, x + radius, z + radius);
    }

    public Node lock(int fromX, int fromZ, int toX, int toZ) {
        int fromCellX = fromX >> shift;
        int fromCellZ = fromZ >> shift;
        int toCellX = toX >> shift;
        int toCellZ = toZ >> shift;
        Node node = new Node(this);

        for (;;) {
            Node conflict = node.claim(cells, fromCellX, fromCellZ, toCellX, toCellZ);
            if (conflict == null) {
                return node;
            }

            // threads that parked on us while we briefly held cells must retry now
            removeCells(node);
            node.closeAndWake();
            if (conflict.addWaiter(Thread.currentThread())) {
                LockSupport.park();
            }

            node.reopen();
        }
    }

    public void unlock(Node node) {
        if (node.lock != this) {
            throw new IllegalStateException("Node belongs to another lock");
        }

        removeCells(node);
        node.closeAndWake();
    }

    private void removeCells(Node node) {
        for (long key : node.acquired) {
            if (cells.remove(key) != node) {
                throw new IllegalStateException("Cell " + key + " was not owned by the unlocking node");
            }
        }

        node.acquired.clear();
    }

    private static long cellKey(int cellX, int cellZ) {
        return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
    }

    public static final class Node {
        private final AreaLock lock;
        private final LongArrayList acquired = new LongArrayList();
        private final ArrayDeque<Thread> waiters = new ArrayDeque<>();
        private boolean closed;

        private Node(AreaLock lock) {
            this.lock = lock;
        }

        /** Takes every free cell of the area; the first cell held by another node stops the scan and is returned. */
        private Node claim(ConcurrentLong2ObjectMap<Node> cells, int fromCellX, int fromCellZ, int toCellX, int toCellZ) {
            for (int cellZ = fromCellZ; cellZ <= toCellZ; ++cellZ) {
                for (int cellX = fromCellX; cellX <= toCellX; ++cellX) {
                    long key = cellKey(cellX, cellZ);
                    Node previous = cells.putIfAbsent(key, this);
                    if (previous != null) {
                        return previous;
                    }

                    acquired.add(key);
                }
            }

            return null;
        }

        private synchronized boolean addWaiter(Thread waiter) {
            if (closed) {
                return false;
            }

            waiters.add(waiter);
            return true;
        }

        private synchronized void reopen() {
            closed = false;
        }

        /** Closing before unparking guarantees late waiters observe the release and retry instead of parking. */
        private void closeAndWake() {
            List<Thread> parked;
            synchronized (this) {
                closed = true;
                parked = List.copyOf(waiters);
                waiters.clear();
            }

            for (Thread waiter : parked) {
                LockSupport.unpark(waiter);
            }
        }
    }
}
