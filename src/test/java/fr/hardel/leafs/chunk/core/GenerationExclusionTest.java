package fr.hardel.leafs.chunk.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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

    /** Two FEATURES steps four chunks apart write and read overlapping blocks; the second must wait for the first. */
    @Test
    void neighbouringFeaturesExclude() throws InterruptedException {
        GenerationExclusion exclusion = new GenerationExclusion();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondRan = new AtomicBoolean();

        Thread first = new Thread(() -> exclusion.runStep(ChunkStatus.FEATURES, new ChunkPos(0, 0), () -> {
            firstEntered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            return CompletableFuture.<ChunkAccess>completedFuture(null);
        }));
        first.start();
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

        Thread second = new Thread(() -> exclusion.runStep(ChunkStatus.FEATURES, new ChunkPos(3, 0), () -> {
            secondRan.set(true);
            return CompletableFuture.<ChunkAccess>completedFuture(null);
        }));
        second.start();
        second.join(200);
        assertFalse(secondRan.get());

        release.countDown();
        second.join(5000);
        first.join(5000);
        assertTrue(secondRan.get());
    }

    /** A status that only writes its own chunk never waits, even inside a held FEATURES area. */
    @Test
    void ownChunkStatusesStayFree() throws InterruptedException {
        GenerationExclusion exclusion = new GenerationExclusion();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread first = new Thread(() -> exclusion.runStep(ChunkStatus.FEATURES, new ChunkPos(0, 0), () -> {
            firstEntered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            return CompletableFuture.<ChunkAccess>completedFuture(null);
        }));
        first.start();
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

        AtomicBoolean ran = new AtomicBoolean();
        Thread free = new Thread(() -> exclusion.runStep(ChunkStatus.NOISE, new ChunkPos(1, 0), () -> {
            ran.set(true);
            return CompletableFuture.<ChunkAccess>completedFuture(null);
        }));
        free.start();
        free.join(5000);
        assertTrue(ran.get());

        release.countDown();
        first.join(5000);
    }
}
