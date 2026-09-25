package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import net.minecraft.network.PacketProcessor;
import org.jspecify.annotations.Nullable;

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

    public void add(PacketProcessor.ListenerAndPacket<?> entry) {
        packets.add(new Entry(entry::handle, entry.packet().getClass().getSimpleName(), System.nanoTime()));
    }

    public void addTask(Runnable task) {
        packets.add(new Entry(task, "Handler continuation", System.nanoTime()));
    }

    public boolean handledByCurrentThread() {
        return DRAINING.get() == this;
    }

    // Used by the Leafs Debug mod
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
            restoreDrainer(outer);
            claimed.set(false);
        }

        return true;
    }

    private static void restoreDrainer(@Nullable PlayerPacketQueue outer) {
        if (outer == null) {
            DRAINING.remove();
            return;
        }

        DRAINING.set(outer);
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
                slowestEntry = next.name();
            }

            next.work().run();
        }

        if (slowestAge > QUEUE_AGE_WARN_NANOS) {
            Leafs.LOGGER.warn("{} waited {} ms in a player's packet queue before handling", slowestEntry, slowestAge / 1_000_000L);
        }
    }

    private record Entry(Runnable work, String name, long enqueuedNanos) {
    }
}
