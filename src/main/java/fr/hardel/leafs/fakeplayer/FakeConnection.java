package fr.hardel.leafs.fakeplayer;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/** No channel, packets dropped: the bot exists only server-side. */
final class FakeConnection extends Connection {

    FakeConnection() {
        super(PacketFlow.SERVERBOUND);
    }

    /** Vanilla assigns the listener then reconfigures the netty pipeline; only the listener exists here. */
    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
        this.packetListener = listener;
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocol) {
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }
}
