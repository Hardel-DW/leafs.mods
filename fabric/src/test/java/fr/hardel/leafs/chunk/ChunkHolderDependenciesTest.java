package fr.hardel.leafs.chunk;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The send barrier of a holder under two owners: vanilla composes it by a read then a write, and one of two lights added at once is lost. */
@ExtendWith(MinecraftBootstrap.class)
class ChunkHolderDependenciesTest {
    private static final long PATIENCE_SECONDS = 5;

    /** 2026-09-16: the light of two chunks of one send radius is registered by two owners; the barrier kept one, the sender shipped a dark chunk. */
    @Test
    void twoLightsAddedAtOnceBothHoldTheSendBarrier() throws InterruptedException {
        ChunkHolder holder = holder();
        PausedBarrier barrier = new PausedBarrier();
        holder.addSendDependency(barrier);
        CompletableFuture<Void> firstLight = new CompletableFuture<>();
        CompletableFuture<Void> secondLight = new CompletableFuture<>();

        Thread firstOwner = Thread.ofPlatform().daemon().start(() -> holder.addSendDependency(firstLight));
        barrier.awaitComposition();
        Thread secondOwner = Thread.ofPlatform().daemon().start(() -> holder.addSendDependency(secondLight));
        awaitDoneOrWaiting(secondOwner);
        barrier.resume();
        firstOwner.join(TimeUnit.SECONDS.toMillis(PATIENCE_SECONDS));
        secondOwner.join(TimeUnit.SECONDS.toMillis(PATIENCE_SECONDS));

        barrier.complete(null);
        firstLight.complete(null);
        assertFalse(holder.getSendSyncFuture().isDone(), "the second light was lost by the concurrent registration");
        secondLight.complete(null);
        assertTrue(holder.getSendSyncFuture().isDone(), "the barrier opens once both lights are done");
    }

    private static ChunkHolder holder() {
        return new ChunkHolder(new ChunkPos(0, 0), ChunkLevel.byStatus(ChunkStatus.FULL), LevelHeightAccessor.create(-64, 384), null, (_, _, _, _) -> { }, (_, _) -> List.of());
    }

    /** Done, or parked on a lock the first owner holds: a registration made atomic waits here instead of finishing. */
    private static void awaitDoneOrWaiting(Thread owner) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PATIENCE_SECONDS);
        while (owner.isAlive() && !parked(owner)) {
            assertTrue(System.nanoTime() < deadline, "the second owner neither finished nor waited for the first");
            Thread.onSpinWait();
        }
    }

    private static boolean parked(Thread thread) {
        return switch (thread.getState()) {
            case BLOCKED, WAITING, TIMED_WAITING -> true;
            default -> false;
        };
    }

    private static void await(CountDownLatch latch, String what) {
        try {
            assertTrue(latch.await(PATIENCE_SECONDS, TimeUnit.SECONDS), what);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(what, exception);
        }
    }

    /** The barrier already in the holder: the first owner composing on it stops inside the composition, before its write of the new barrier. */
    private static final class PausedBarrier extends CompletableFuture<Void> {
        private final AtomicBoolean pauseOnce = new AtomicBoolean(true);
        private final CountDownLatch composing = new CountDownLatch(1);
        private final CountDownLatch resumed = new CountDownLatch(1);

        void awaitComposition() {
            await(composing, "the first owner reached the composition");
        }

        void resume() {
            resumed.countDown();
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super Void, ? super U, ? extends V> function) {
            CompletableFuture<V> combined = super.thenCombine(other, function);
            if (pauseOnce.getAndSet(false)) {
                composing.countDown();
                await(resumed, "the paused owner was resumed");
            }

            return combined;
        }
    }
}
