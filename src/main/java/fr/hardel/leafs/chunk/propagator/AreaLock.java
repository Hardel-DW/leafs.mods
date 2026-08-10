package fr.hardel.leafs.chunk.propagator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * Reentrant lock over rectangular areas, keyed by {@code coordinate >> shift} cells. Acquisition
 * inserts one node per cell and rolls back completely on conflict before parking on the owning
 * node, so a blocked thread never holds a cell and two areas cannot deadlock each other.
 * Re-locking cells already owned by the current thread returns an empty node whose unlock is a
 * no-op; an area may not partially overlap cells the thread already owns.
 */
public final class AreaLock {

    private final int shift;
    private final ConcurrentHashMap<Long, Node> cells = new ConcurrentHashMap<>();

    public AreaLock(int shift) {
        this.shift = shift;
    }

    public Node lock(int x, int z, int radius) {
        return lock(x - radius, z - radius, x + radius, z + radius);
    }

    public Node lock(int fromX, int fromZ, int toX, int toZ) {
        Thread thread = Thread.currentThread();
        int fromCellX = fromX >> shift;
        int fromCellZ = fromZ >> shift;
        int toCellX = toX >> shift;
        int toCellZ = toZ >> shift;

        List<Long> acquired = new ArrayList<>();
        Node node = new Node(this, acquired, thread);

        for (;;) {
            Node conflict = null;
            boolean ownedSome = false;

            scan:
            for (int cellZ = fromCellZ; cellZ <= toCellZ; ++cellZ) {
                for (int cellX = fromCellX; cellX <= toCellX; ++cellX) {
                    Long key = cellKey(cellX, cellZ);
                    Node previous = cells.putIfAbsent(key, node);
                    if (previous == null) {
                        acquired.add(key);
                        continue;
                    }
                    if (previous.thread != thread) {
                        conflict = previous;
                        break scan;
                    }
                    ownedSome = true;
                }
            }

            if (conflict == null) {
                if (ownedSome && !acquired.isEmpty()) {
                    throw new IllegalStateException("Area partially overlaps cells already owned by " + thread);
                }
                return node;
            }

            boolean inserted = !acquired.isEmpty();
            if (inserted) {
                removeCells(node);
                acquired.clear();
                // threads that parked on us while we briefly held cells must retry now
                node.closeAndWake();
            }

            // a false add means the conflicting node was released in between, just retry
            if (conflict.addWaiter(thread)) {
                LockSupport.park();
            }

            if (inserted) {
                synchronized (node) {
                    node.closed = false;
                }
            }
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
        for (Long key : node.acquired) {
            if (cells.remove(key) != node) {
                throw new IllegalStateException("Cell " + key + " was not owned by the unlocking node");
            }
        }
    }

    private static Long cellKey(int cellX, int cellZ) {
        return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
    }

    public static final class Node {
        private final AreaLock lock;
        private final List<Long> acquired;
        private final Thread thread;
        private final ArrayDeque<Thread> waiters = new ArrayDeque<>();
        private boolean closed;

        private Node(AreaLock lock, List<Long> acquired, Thread thread) {
            this.lock = lock;
            this.acquired = acquired;
            this.thread = thread;
        }

        private synchronized boolean addWaiter(Thread waiter) {
            if (closed) {
                return false;
            }

            waiters.add(waiter);
            return true;
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
