package fr.hardel.leafs.network;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        queue.drain();

        assertEquals(List.of("first", "second"), handled);
    }

    @Test
    void disconnectedListenerPacketsAreDropped() {
        FakeListener disconnected = new FakeListener(false);
        queue.add(disconnected, new FakePacket("dropped", handled::add));

        queue.drain();

        assertEquals(List.of(), handled);
    }

    /** {@code ServerGamePacketListenerImpl} keeps accepting the reconfiguration ack after it stops accepting the rest. */
    @Test
    void skippablePacketsBypassTheAcceptingCheck() {
        FakeListener listener = new FakeListener(false);
        listener.alwaysHandled = "config_ack";
        queue.add(listener, new FakePacket("dropped", handled::add));
        queue.add(listener, new FakePacket("config_ack", handled::add));

        queue.drain();

        assertEquals(List.of("config_ack"), handled);
    }

    @Test
    void packetsQueuedDuringTheDrainAreHandledInTheSameDrain() {
        FakeListener listener = new FakeListener(true);
        queue.add(listener, new FakePacket("first", name -> {
            handled.add(name);
            queue.add(listener, new FakePacket("late", handled::add));
        }));

        queue.drain();

        assertEquals(List.of("first", "late"), handled);
    }

    /** Vanilla's global drain has no per-packet recovery either: {@code onPacketError} rethrows and the tick dies. */
    @Test
    void handlerFailureEscapesTheDrainAndLeavesTheRestQueued() {
        FakeListener listener = new FakeListener(true);
        queue.add(listener, new FakePacket("boom", name -> {
            throw new IllegalStateException(name);
        }));
        queue.add(listener, new FakePacket("survivor", handled::add));

        assertThrows(RuntimeException.class, queue::drain);

        assertEquals(List.of(), handled);
        assertEquals(1, listener.errors.size());
        assertFalse(PlayerPacketQueue.handlingPackets(), "the handling scope must not leak past a failing drain");

        queue.drain();
        assertEquals(List.of("survivor"), handled);
    }

    /**
     * The regression that crashed the server on an ordinary death: the routing hook re-queued and
     * rethrew because it re-read the listener's player, which respawn had just moved to another
     * level. The queue owns every packet it drains, whatever a handler does to the player.
     */
    @Test
    void ownershipHoldsForEveryPacketOfADrainAcrossAPlayerSwap() {
        FakeListener listener = new FakeListener(true);
        List<Boolean> owned = new ArrayList<>();
        queue.add(listener, new FakePacket("perform_respawn", _ -> {
            listener.player = "respawned in another level";
            owned.add(queue.handledByCurrentThread());
        }));
        queue.add(listener, new FakePacket("move_player", _ -> owned.add(queue.handledByCurrentThread())));

        queue.drain();

        assertEquals("respawned in another level", listener.player);
        assertEquals(List.of(true, true), owned);
        assertFalse(queue.handledByCurrentThread(), "the scope is thread-local to the drain");
    }

    /** A handler that moves the player mid-drain ends the drain; the rest waits for the new owner. */
    @Test
    void aDrainStopsWhenOwnershipMovesMidDrain() {
        FakeListener listener = new FakeListener(true);
        queue.add(listener, new FakePacket("teleport", _ -> {
            handled.add("teleport");
            listener.player = "moved to the nether";
        }));
        queue.add(listener, new FakePacket("stays_queued", handled::add));

        queue.drain(() -> listener.player.equals("initial level"));
        assertEquals(List.of("teleport"), handled);

        queue.drain(() -> listener.player.equals("moved to the nether"));
        assertEquals(List.of("teleport", "stays_queued"), handled);
    }

    /** Why the gate must never re-queue mid-drain: the stackless rethrow becomes a reported crash. */
    @Test
    void aRethrowInsideTheDrainEscalatesToACrash() {
        FakeListener listener = new FakeListener(true);
        queue.add(listener, new FakePacket("requeued", _ -> {
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }));

        RuntimeException crash = assertThrows(RuntimeException.class, queue::drain);

        assertInstanceOf(RunningOnDifferentThreadException.class, crash.getCause());
        assertEquals(1, listener.errors.size());
    }

    /** Two units racing a handover must never run handlers concurrently: the loser skips, the queue survives. */
    @Test
    void aClaimedQueueRefusesASecondDrainer() throws InterruptedException {
        FakeListener listener = new FakeListener(true);
        CountDownLatch insideDrain = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        queue.add(listener, new FakePacket("blocker", _ -> {
            insideDrain.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));

        Thread regionDrainer = new Thread(() -> queue.drain());
        regionDrainer.start();
        assertTrue(insideDrain.await(5, TimeUnit.SECONDS));

        assertFalse(queue.drain(() -> true), "the global loop must back off while the region drains");
        release.countDown();
        regionDrainer.join();

        assertTrue(queue.drain(() -> true), "the claim releases with the drain");
    }

    /** The global loop adopts a listener only when no region has stamped it recently. */
    @Test
    void regionOwnerStampStartsStaleAndFreshens() {
        assertFalse(queue.regionOwnerFresh(TimeUnit.MILLISECONDS.toNanos(250)), "never stamped = global-owned");

        queue.stampRegionOwner();
        assertTrue(queue.regionOwnerFresh(TimeUnit.MILLISECONDS.toNanos(250)));
        assertFalse(queue.regionOwnerFresh(-1), "an already-expired horizon reads stale");
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

        queue.drain();

        assertEquals(threads * perThread, handled.size());
        for (int thread = 0; thread < threads; thread++) {
            String prefix = "t" + thread + ":";
            List<String> sequence = handled.stream().filter(name -> name.startsWith(prefix)).toList();
            for (int index = 0; index < perThread; index++) {
                assertEquals(prefix + index, sequence.get(index), "per-player submissions must stay ordered");
            }
        }
    }

    /** Mirrors {@code ServerCommonPacketListenerImpl}: it records the failure AND rethrows it. */
    private static final class FakeListener implements PacketListener {
        final List<Exception> errors = new ArrayList<>();
        private final boolean accepting;
        String player = "initial level";
        String alwaysHandled;

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
        public boolean shouldHandleMessage(Packet<?> packet) {
            return PacketListener.super.shouldHandleMessage(packet)
                || (packet instanceof FakePacket fake && fake.name().equals(alwaysHandled));
        }

        @Override
        public void onPacketError(Packet packet, Exception cause) {
            errors.add(cause);
            throw new RuntimeException("Main thread packet handler", cause);
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
