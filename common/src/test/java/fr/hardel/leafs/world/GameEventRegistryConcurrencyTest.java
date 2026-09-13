package fr.hardel.leafs.world;

import fr.hardel.MinecraftBootstrap;
import fr.hardel.excess.ConcurrentInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class GameEventRegistryConcurrencyTest {

    @Test
    void concurrentLookupsOfOneSectionMustReturnTheSameRegistry() throws Exception {
        Int2ObjectMap<GameEventListenerRegistry> sections = new ConcurrentInt2ObjectMap<>();
        CountDownLatch creating = new CountDownLatch(1);
        CountDownLatch finishCreation = new CountDownLatch(1);
        CountDownLatch secondLookup = new CountDownLatch(1);
        AtomicInteger creations = new AtomicInteger();
        IntFunction<GameEventListenerRegistry> factory = section -> {
            if (creations.incrementAndGet() == 1) {
                creating.countDown();
                try {
                    assertTrue(finishCreation.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }

            return new EuclideanGameEventListenerRegistry(null, section, _ -> { });
        };

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> sections.computeIfAbsent(4, factory));
            try {
                assertTrue(creating.await(5, TimeUnit.SECONDS));
                var second = workers.submit(() -> {
                    secondLookup.countDown();
                    return sections.computeIfAbsent(4, factory);
                });
                assertTrue(secondLookup.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
                finishCreation.countDown();

                assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
                assertSame(first.get(), sections.get(4));
                assertEquals(1, creations.get());
            } finally {
                finishCreation.countDown();
            }
        }
    }
}
