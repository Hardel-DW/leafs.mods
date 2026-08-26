package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** One player's inbound packets, drained by the owning unit. The draining thread is the packet-handling thread for that listener. */
public final class PlayerPacketQueue {
    private static final ThreadLocal<PlayerPacketQueue> DRAINING = new ThreadLocal<>();
    private static final long QUEUE_AGE_WARN_NANOS = 250_000_000L;

    private final ConcurrentLinkedDeque<Entry> packets = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean claimed = new AtomicBoolean();
    private volatile long regionStampNanos;

    /** Vanilla's "am I the packet-handling thread", asked without a listener in scope (Fabric's receive-thread check). */
    public static boolean handlingPackets() {
        return DRAINING.get() != null;
    }

    /** The owning region's liveness mark: while fresh, the global loop leaves this listener alone. */
    public void stampRegionOwner() {
        regionStampNanos = System.nanoTime();
    }

    public boolean regionOwnerFresh(long staleNanos) {
        long stamp = regionStampNanos;
        return stamp != 0 && System.nanoTime() - stamp < staleNanos;
    }

    public <T extends PacketListener> void add(T listener, Packet<T> packet) {
        packets.add(new QueuedPacket<>(listener, packet, System.nanoTime()));
    }

    /** Handler continuations (chat chain, text filtering) run in packet order on the player's owner. */
    public void addTask(Runnable task) {
        packets.add(new QueuedContinuation(task, System.nanoTime()));
    }

    public boolean handledByCurrentThread() {
        return DRAINING.get() == this;
    }

    /** Debug sampling only: the concurrent queue counts its nodes, O(n) on a handful of waiting packets. */
    public int pending() {
        return packets.size();
    }

    /** Runs one handler as this listener's packet-handling thread; false when another thread is draining, the caller retries. */
    public boolean handleAs(Runnable handler) {
        if (handledByCurrentThread()) {
            handler.run();
            return true;
        }

        if (!claimed.compareAndSet(false, true)) {
            return false;
        }

        PlayerPacketQueue outer = DRAINING.get();
        DRAINING.set(this);
        try {
            handler.run();
        } finally {
            if (outer == null) {
                DRAINING.remove();
            } else {
                DRAINING.set(outer);
            }

            claimed.set(false);
        }

        return true;
    }

    /** Vanilla {@code processQueuedPackets} semantics: everything queued, including what handlers queue back. */
    public void drain() {
        drain(() -> true);
    }

    /**
     * Stops when the caller loses the player mid-drain (respawn, teleport): the rest waits for the
     * new owner. Returns false without draining when another thread holds the queue, so two units
     * racing a handover never run handlers concurrently.
     */
    public boolean drain(BooleanSupplier ownerHolds) {
        if (handledByCurrentThread()) {
            drainLoop(ownerHolds);
            return true;
        }

        if (!claimed.compareAndSet(false, true)) {
            return false;
        }

        PlayerPacketQueue outer = DRAINING.get();
        DRAINING.set(this);
        try {
            drainLoop(ownerHolds);
        } finally {
            if (outer == null) {
                DRAINING.remove();
            } else {
                DRAINING.set(outer);
            }

            claimed.set(false);
        }

        return true;
    }

    private void drainLoop(BooleanSupplier ownerHolds) {
        long slowestAge = 0;
        String slowestEntry = null;
        Entry next;
        while (ownerHolds.getAsBoolean() && (next = packets.poll()) != null) {
            long age = System.nanoTime() - next.enqueuedNanos();
            if (age > slowestAge) {
                slowestAge = age;
                slowestEntry = next.describe();
            }

            try {
                next.handle();
            } catch (OwnershipViolationException _) {
                packets.addFirst(next);
                break;
            }
        }

        if (slowestAge > QUEUE_AGE_WARN_NANOS) {
            Leafs.LOGGER.warn("{} waited {} ms in a player's packet queue before handling", slowestEntry, slowestAge / 1_000_000L);
        }
    }

    private interface Entry {
        void handle();

        long enqueuedNanos();

        String describe();
    }

    private record QueuedContinuation(Runnable task, long enqueuedNanos) implements Entry {
        @Override
        public void handle() {
            task.run();
        }

        @Override
        public String describe() {
            return "Handler continuation";
        }
    }

    private record QueuedPacket<T extends PacketListener>(T listener, Packet<T> packet, long enqueuedNanos) implements Entry {
        @Override
        public String describe() {
            return packet.getClass().getSimpleName();
        }

        @Override
        public void handle() {
            if (!listener.shouldHandleMessage(packet)) {
                Leafs.LOGGER.debug("Ignoring packet due to disconnection: {}", packet);
                return;
            }

            try {
                packet.handle(listener);
            } catch (Exception exception) {
                if (exception instanceof OwnershipViolationException refusal) {
                    throw refusal;
                }

                if (exception instanceof ReportedException reported && reported.getCause() instanceof OutOfMemoryError) {
                    throw PacketUtils.makeReportedException(exception, packet, listener);
                }

                listener.onPacketError(packet, exception);
            }
        }
    }
}
