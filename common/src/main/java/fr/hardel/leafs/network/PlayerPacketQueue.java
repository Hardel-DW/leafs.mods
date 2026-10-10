package fr.hardel.leafs.network;

import net.minecraft.network.PacketProcessor;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class PlayerPacketQueue {
    private static final ThreadLocal<PlayerPacketQueue> DRAINING = new ThreadLocal<>();

    private final ConcurrentLinkedDeque<Runnable> packets = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean claimed = new AtomicBoolean();
    private volatile boolean handedOver;

    public static boolean handlingPackets() {
        return DRAINING.get() != null;
    }

    public void add(PacketProcessor.ListenerAndPacket<?> entry) {
        packets.add(entry::handle);
    }

    public void addTask(Runnable task) {
        packets.add(task);
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
        Runnable next;
        while (!handedOver && ownerHolds.getAsBoolean() && (next = packets.poll()) != null) {
            next.run();
        }
    }
}
