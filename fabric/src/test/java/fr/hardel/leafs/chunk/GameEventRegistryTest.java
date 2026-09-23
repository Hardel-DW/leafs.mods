package fr.hardel.leafs.chunk;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.TestThreads;
import fr.hardel.leafs.world.GameEventListeners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class GameEventRegistryTest {

    @Test
    void vanillaTraversalSurvivesAnotherVisitUnregisteringItsNextListener() throws Exception {
        EuclideanGameEventListenerRegistry registry = new EuclideanGameEventListenerRegistry(null, 0, _ -> { });
        var field = EuclideanGameEventListenerRegistry.class.getDeclaredField("listeners");
        field.setAccessible(true);
        GameEventListeners listeners = assertInstanceOf(GameEventListeners.class, field.get(registry));
        GameEventListener first = new Listener(1);
        GameEventListener second = new Listener(2);
        listeners.addAll(List.of(first, second));
        CountDownLatch visiting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<GameEventListener> seen = new ArrayList<>();

        try (var workers = Executors.newFixedThreadPool(2)) {
            var outer = workers.submit(() -> registry.visitInRangeListeners(GameEvent.STEP, Vec3.ZERO, new GameEvent.Context(null, null), (listener, _) -> {
                seen.add(listener);
                visiting.countDown();
                TestThreads.await(release);
            }));
            try {
                assertTrue(visiting.await(5, TimeUnit.SECONDS));
                var concurrent = workers.submit(() -> registry.visitInRangeListeners(GameEvent.STEP, Vec3.ZERO, new GameEvent.Context(null, null),
                    (_, _) -> registry.unregister(second)));
                assertTrue(concurrent.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }

            assertTrue(outer.get(5, TimeUnit.SECONDS));
        }

        assertEquals(List.of(first), seen);
        assertEquals(List.of(first), List.copyOf(listeners));
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
