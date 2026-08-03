package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One player's inbound packets, drained by the unit owning the player. Handling replicates vanilla
 * {@code PacketProcessor.ListenerAndPacket} exactly: disconnected-listener drop, per-packet error
 * recovery, OOM escalation.
 *
 * <p>The drain also PUBLISHES the answer both routing hooks need: while a queue drains, the draining
 * thread is the packet-handling thread of that listener. That is the only durable ownership signal -
 * {@code ServerGamePacketListenerImpl.player} is reassigned mid-drain by respawn, so anything derived
 * from the player (its level, its region) flips between two packets of the same drain.
 */
public final class PlayerPacketQueue {
    private static final ThreadLocal<PlayerPacketQueue> DRAINING = new ThreadLocal<>();

    private final ConcurrentLinkedQueue<QueuedPacket<?>> packets = new ConcurrentLinkedQueue<>();

    /** Vanilla's "am I the packet-handling thread", asked without a listener in scope (Fabric's receive-thread check). */
    public static boolean handlingPackets() {
        return DRAINING.get() != null;
    }

    public <T extends PacketListener> void add(T listener, Packet<T> packet) {
        packets.add(new QueuedPacket<>(listener, packet));
    }

    public boolean handledByCurrentThread() {
        return DRAINING.get() == this;
    }

    /** Vanilla {@code processQueuedPackets} semantics: everything queued, including what handlers queue back. */
    public void drain() {
        PlayerPacketQueue outer = DRAINING.get();
        DRAINING.set(this);
        try {
            QueuedPacket<?> next;
            while ((next = packets.poll()) != null) {
                next.handle();
            }
        } finally {
            if (outer == null) {
                DRAINING.remove();
            } else {
                DRAINING.set(outer);
            }
        }
    }

    private record QueuedPacket<T extends PacketListener>(T listener, Packet<T> packet) {
        void handle() {
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
