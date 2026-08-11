package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** One player's inbound packets, drained by the owning unit. The draining thread is the packet-handling thread for that listener. */
public final class PlayerPacketQueue {
    private static final ThreadLocal<PlayerPacketQueue> DRAINING = new ThreadLocal<>();
    private static final long QUEUE_AGE_WARN_NANOS = 250_000_000L;

    private final ConcurrentLinkedQueue<Entry> packets = new ConcurrentLinkedQueue<>();
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

    /** The queue-age tracer measures the latency a player feels between sending an action and its handling. */
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

            next.handle();
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
                if (exception instanceof ReportedException reported && reported.getCause() instanceof OutOfMemoryError) {
                    throw PacketUtils.makeReportedException(exception, packet, listener);
                }

                listener.onPacketError(packet, exception);
            }
        }
    }
}
