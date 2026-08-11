package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.chunk.propagator.AreaLock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Spatial exclusion of the generation steps that cross chunk boundaries, one AreaLock per level at
 * chunk granularity. FEATURES writes one chunk out and reads two (sculk patches), the light steps
 * read two through the engine, SPAWN and INITIALIZE_LIGHT read their own blocks that a neighbouring
 * FEATURES may be writing. Steps that only touch their own chunk stay free, the layer protocol of
 * ChunkGenerationTask already orders their readers. Async steps are joined under the lock, because
 * the exclusion must cover the work, not the scheduling of the work.
 */
public final class GenerationExclusion {

    /** FULL runs on the owning region and takes this radius there, never on a pool worker. */
    public static final int FULL_STEP_RADIUS = 1;

    private final AreaLock lock = new AreaLock(0);

    public CompletableFuture<ChunkAccess> runStep(ChunkStatus status, ChunkPos pos, Supplier<CompletableFuture<ChunkAccess>> step) {
        int radius = radiusOf(status);
        if (radius < 0) {
            return step.get();
        }

        return runExcluded(pos, radius, step);
    }

    private CompletableFuture<ChunkAccess> runExcluded(ChunkPos pos, int radius, Supplier<CompletableFuture<ChunkAccess>> step) {
        AreaLock.Node node = lock.lock(pos.x(), pos.z(), radius);
        try {
            CompletableFuture<ChunkAccess> future = step.get();
            try {
                future.join();
            } catch (CompletionException | CancellationException failure) {
                // completion is what the lock must cover; the failure stays in the future for the vanilla handlers
            }

            return future;
        } finally {
            lock.unlock(node);
        }
    }

    /** Synchronous scope for the FULL step body, run on the owning thread with the neighbouring FEATURES held off. */
    public <T> T supplyExcluded(ChunkPos pos, int radius, Supplier<T> body) {
        AreaLock.Node node = lock.lock(pos.x(), pos.z(), radius);
        try {
            return body.get();
        } finally {
            lock.unlock(node);
        }
    }

    private static int radiusOf(ChunkStatus status) {
        if (status == ChunkStatus.FEATURES || status == ChunkStatus.LIGHT) {
            return 2;
        }

        if (status == ChunkStatus.INITIALIZE_LIGHT || status == ChunkStatus.SPAWN) {
            return 0;
        }

        return -1;
    }
}
