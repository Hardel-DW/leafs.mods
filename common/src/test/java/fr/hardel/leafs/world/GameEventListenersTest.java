package fr.hardel.leafs.world;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class GameEventListenersTest {
    private final GameEventListeners listeners = new GameEventListeners();
    private final GameEventListener first = new Listener(1);
    private final GameEventListener second = new Listener(2);
    private final GameEventListener added = new Listener(3);

    @Test
    void aCallbackRemovesTheNextListenerAndAddsOneForTheNextVisit() {
        listeners.addAll(List.of(first, second));
        List<GameEventListener> seen = new ArrayList<>();

        visit(listener -> {
            seen.add(listener);
            listeners.remove(second);
            listeners.add(added);
        });

        assertEquals(List.of(first), seen);
        assertEquals(List.of(first, added), List.copyOf(listeners));
    }

    @Test
    void anUnregisteredThenRegisteredListenerDoesNotReappearInTheCurrentVisit() {
        listeners.addAll(List.of(first, second));
        List<GameEventListener> seen = new ArrayList<>();

        visit(listener -> {
            seen.add(listener);
            listeners.remove(second);
            listeners.add(second);
        });

        assertEquals(List.of(first), seen);
        assertEquals(List.of(first, second), List.copyOf(listeners));
    }

    @Test
    void nestedVisitsDoNotPublishPendingRegistrationsIntoTheOuterVisit() {
        listeners.add(first);
        List<GameEventListener> nested = new ArrayList<>();

        visit(_ -> {
            listeners.add(added);
            visit(nested::add);
            assertEquals(List.of(first), List.copyOf(listeners));
        });

        assertEquals(List.of(first), nested);
        assertEquals(List.of(first, added), List.copyOf(listeners));
    }

    @Test
    void aPendingRegistrationCanBeRemovedBeforePublication() {
        listeners.add(first);
        visit(_ -> {
            listeners.add(added);
            assertTrue(listeners.remove(added));
        });

        assertEquals(List.of(first), List.copyOf(listeners));
    }

    @Test
    void aThrowingCallbackClosesItsVisitAndPublishesPendingRegistrations() {
        listeners.add(first);

        assertThrows(IllegalStateException.class, () -> visit(_ -> {
            listeners.add(added);
            throw new IllegalStateException("listener failure");
        }));

        assertEquals(List.of(first, added), List.copyOf(listeners));
    }

    @Test
    void removalBetweenHasNextAndNextDoesNotBreakAnInFlightSelection() {
        listeners.add(first);
        var iterator = listeners.iterator();
        assertTrue(iterator.hasNext());

        listeners.remove(first);

        assertSame(first, iterator.next());
        assertFalse(iterator.hasNext());
        assertTrue(listeners.isEmpty());
    }

    @Test
    void aCallbackDoesNotHoldTheListLockWhileAnotherThreadRegistersAndVisits() throws Exception {
        listeners.addAll(List.of(first, second));
        CountDownLatch visiting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<GameEventListener> seen = new ArrayList<>();

        try (var workers = Executors.newFixedThreadPool(2)) {
            var outer = workers.submit(() -> visit(listener -> {
                seen.add(listener);
                visiting.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }));
            try {
                assertTrue(visiting.await(5, TimeUnit.SECONDS));
                var concurrent = workers.submit(() -> {
                    listeners.remove(second);
                    listeners.add(added);
                    List<GameEventListener> nested = new ArrayList<>();
                    visit(nested::add);
                    return nested;
                });

                assertEquals(List.of(first), concurrent.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            outer.get(5, TimeUnit.SECONDS);
        }

        assertEquals(List.of(first), seen);
        assertEquals(List.of(first, added), List.copyOf(listeners));
    }

    private void visit(Consumer<GameEventListener> action) {
        listeners.beginVisit();
        try {
            listeners.forEach(action);
        } finally {
            listeners.endVisit();
        }
    }

    private record Listener(int id) implements GameEventListener {
        @Override
        public PositionSource getListenerSource() {
            return new BlockPositionSource(BlockPos.ZERO);
        }

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 sourcePosition) {
            return false;
        }
    }
}
