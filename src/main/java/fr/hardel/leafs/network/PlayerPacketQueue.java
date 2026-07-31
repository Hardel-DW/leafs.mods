package fr.hardel.leafs.network;

import fr.hardel.leafs.Leafs;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One player's inbound packets, drained by the region that owns the player. Handling replicates
 * vanilla {@code PacketProcessor.ListenerAndPacket} exactly: disconnected-listener drop, per-packet
 * error recovery, OOM escalation.
 */
public final class PlayerPacketQueue {
    private final ConcurrentLinkedQueue<QueuedPacket<?>> packets = new ConcurrentLinkedQueue<>();

    public <T extends PacketListener> void add(T listener, Packet<T> packet) {
        packets.add(new QueuedPacket<>(listener, packet));
    }

    public int drain() {
        int budget = packets.size();
        int handled = 0;
        QueuedPacket<?> next;
        while (handled < budget && (next = packets.poll()) != null) {
            next.handle();
            handled++;
        }

        return handled;
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
