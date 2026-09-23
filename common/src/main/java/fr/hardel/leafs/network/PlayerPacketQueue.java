package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class PlayerPacketQueue {
    private static final ThreadLocal<PlayerPacketQueue> DRAINING = new ThreadLocal<>();
    private static final long QUEUE_AGE_WARN_NANOS = 250_000_000L;

    private final ConcurrentLinkedDeque<Entry> packets = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean claimed = new AtomicBoolean();
    private volatile boolean handedOver;

    public static boolean handlingPackets() {
        return DRAINING.get() != null;
    }

    public <T extends PacketListener> void add(T listener, Packet<T> packet) {
        packets.add(new QueuedPacket<>(listener, packet, System.nanoTime()));
    }

    public void addTask(Runnable task) {
        packets.add(new QueuedContinuation(task, System.nanoTime()));
    }

    public boolean handledByCurrentThread() {
        return DRAINING.get() == this;
    }

    public int pending() {
        return packets.size();
    }

    public void handOver() {
        handedOver = true;
    }

    public boolean handleAs(Runnable handler) {
        if (handledByCurrentThread()) {
            handler.run();
            return true;
        }

        return asDrainer(handler);
    }

    public boolean drain() {
        return drain(() -> true);
    }

    public boolean drain(BooleanSupplier ownerHolds) {
        if (handledByCurrentThread()) {
            drainLoop(ownerHolds);
            return true;
        }

        return asDrainer(() -> drainLoop(ownerHolds));
    }

    private boolean asDrainer(Runnable body) {
        if (!claimed.compareAndSet(false, true)) {
            return false;
        }

        PlayerPacketQueue outer = DRAINING.get();
        DRAINING.set(this);
        try {
            body.run();
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
        handedOver = false;
        long slowestAge = 0;
        String slowestEntry = null;
        Entry next;
        while (!handedOver && ownerHolds.getAsBoolean() && (next = packets.poll()) != null) {
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
