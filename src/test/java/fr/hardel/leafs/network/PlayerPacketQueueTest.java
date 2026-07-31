package fr.hardel.leafs.network;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class PlayerPacketQueueTest {
    private final PlayerPacketQueue queue = new PlayerPacketQueue();
    private final List<String> handled = new ArrayList<>();

    @Test
    void drainHandlesInSubmissionOrder() {
        FakeListener listener = new FakeListener(true);
        queue.add(listener, new FakePacket("first", handled::add));
        queue.add(listener, new FakePacket("second", handled::add));

        assertEquals(2, queue.drain());
        assertEquals(List.of("first", "second"), handled);
        assertEquals(0, queue.drain());
    }

    @Test
    void disconnectedListenerPacketsAreDropped() {
        FakeListener disconnected = new FakeListener(false);
        queue.add(disconnected, new FakePacket("dropped", handled::add));

        assertEquals(1, queue.drain());
        assertEquals(List.of(), handled);
    }

    @Test
    void handlerExceptionsGoThroughOnPacketError() {
        FakeListener listener = new FakeListener(true);
        queue.add(listener, new FakePacket("boom", name -> {
            throw new IllegalStateException(name);
        }));
        queue.add(listener, new FakePacket("survivor", handled::add));

        assertEquals(2, queue.drain());
        assertEquals(List.of("survivor"), handled);
        assertEquals(1, listener.errors.size());
    }

    @Test
    void concurrentSubmissionsKeepPerThreadOrder() throws InterruptedException {
        FakeListener listener = new FakeListener(true);
        int threads = 4;
        int perThread = 250;
        CountDownLatch done = new CountDownLatch(threads);
        for (int thread = 0; thread < threads; thread++) {
            String prefix = "t" + thread + ":";
            Thread submitter = new Thread(() -> {
                for (int sequence = 0; sequence < perThread; sequence++) {
                    queue.add(listener, new FakePacket(prefix + sequence, handled::add));
                }
                done.countDown();
            });
            submitter.start();
        }
        assertTrue(done.await(5, TimeUnit.SECONDS));

        assertEquals(threads * perThread, queue.drain());
        for (int thread = 0; thread < threads; thread++) {
            String prefix = "t" + thread + ":";
            List<String> sequence = handled.stream().filter(name -> name.startsWith(prefix)).toList();
            for (int index = 0; index < perThread; index++) {
                assertEquals(prefix + index, sequence.get(index), "per-player submissions must stay ordered");
            }
        }
    }

    private static final class FakeListener implements PacketListener {
        final List<Exception> errors = new ArrayList<>();
        private final boolean accepting;

        FakeListener(boolean accepting) {
            this.accepting = accepting;
        }

        @Override
        public PacketFlow flow() {
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public ConnectionProtocol protocol() {
            return ConnectionProtocol.PLAY;
        }

        @Override
        public void onDisconnect(DisconnectionDetails details) {
        }

        @Override
        public boolean isAcceptingMessages() {
            return accepting;
        }

        @Override
        public void onPacketError(Packet packet, Exception cause) {
            errors.add(cause);
        }
    }

    private record FakePacket(String name, Consumer<String> onHandle) implements Packet<FakeListener> {

        @Override
        public PacketType<FakePacket> type() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handle(FakeListener listener) {
            onHandle.accept(name);
        }
    }
}
