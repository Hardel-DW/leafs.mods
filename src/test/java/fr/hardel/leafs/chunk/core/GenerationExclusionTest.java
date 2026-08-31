package fr.hardel.leafs.chunk.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationExclusionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ChunkStep step(ChunkStatus status) {
        return ChunkPyramid.GENERATION_PYRAMID.getStepTo(status);
    }

    private static CompletableFuture<ChunkAccess> done() {
        return CompletableFuture.completedFuture(null);
    }

    /** Runs the step on its own thread and reports whether the body ran; the body blocks on the latch when one is given. */
    private static Thread run(GenerationExclusion exclusion, ChunkStatus status, ChunkPos pos, CountDownLatch entered, CountDownLatch release, AtomicBoolean ran) {
        Thread thread = new Thread(() -> exclusion.runStep(step(status), pos, () -> {
            ran.set(true);
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            return done();
        }));
        thread.start();
        return thread;
    }

    /** Two FEATURES steps two chunks apart both write the chunk between them; the second waits for the first. */
    @Test
    void overlappingWritesExclude() throws InterruptedException {
        GenerationExclusion exclusion = new GenerationExclusion();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = run(exclusion, ChunkStatus.FEATURES, new ChunkPos(0, 0), firstEntered, release, new AtomicBoolean());
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

        AtomicBoolean secondRan = new AtomicBoolean();
        Thread second = run(exclusion, ChunkStatus.FEATURES, new ChunkPos(2, 0), new CountDownLatch(1), new CountDownLatch(0), secondRan);
        second.join(200);
        assertFalse(secondRan.get());

        release.countDown();
        second.join(5000);
        first.join(5000);
        assertTrue(secondRan.get());
    }

    /** A step that declares no block writes never waits, even inside a held FEATURES area. */
    @Test
    void nonWritingStepsStayFree() throws InterruptedException {
        GenerationExclusion exclusion = new GenerationExclusion();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = run(exclusion, ChunkStatus.FEATURES, new ChunkPos(0, 0), firstEntered, release, new AtomicBoolean());
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

        AtomicBoolean ran = new AtomicBoolean();
        Thread free = run(exclusion, ChunkStatus.LIGHT, new ChunkPos(0, 0), new CountDownLatch(1), new CountDownLatch(0), ran);
        free.join(5000);
        assertTrue(ran.get());

        release.countDown();
        first.join(5000);
    }

    /** An asynchronous step keeps its area after its thread returned, and gives it back the moment its future completes. */
    @Test
    void asynchronousStepHoldsUntilCompletion() throws InterruptedException {
        GenerationExclusion exclusion = new GenerationExclusion();
        CompletableFuture<ChunkAccess> noise = new CompletableFuture<>();
        exclusion.runStep(step(ChunkStatus.NOISE), new ChunkPos(0, 0), () -> noise);

        AtomicBoolean ran = new AtomicBoolean();
        Thread neighbour = run(exclusion, ChunkStatus.FEATURES, new ChunkPos(1, 0), new CountDownLatch(1), new CountDownLatch(0), ran);
        neighbour.join(200);
        assertFalse(ran.get());

        noise.complete(null);
        neighbour.join(5000);
        assertTrue(ran.get());
    }
}
