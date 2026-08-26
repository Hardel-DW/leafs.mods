package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.chunk.propagator.AreaLock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** Spatial exclusion of the generation steps that cross chunk borders. FEATURES and LIGHT reach two chunks out, SPAWN and INITIALIZE_LIGHT read what a neighbour FEATURES may write. */
public final class GenerationExclusion {

    /** FULL runs on the owning region and takes this radius there, never on a pool worker. */
    public static final int FULL_STEP_RADIUS = 1;

    private final AreaLock lock = new AreaLock(0);

    public CompletableFuture<ChunkAccess> runStep(ChunkStatus status, ChunkPos pos, Supplier<CompletableFuture<ChunkAccess>> step) {
        int radius = radiusOf(status);
        if (radius < 0) {
            return step.get();
        }

        return supplyExcluded(pos, radius, () -> {
            CompletableFuture<ChunkAccess> future = step.get();
            try {
                future.join();
            } catch (CompletionException | CancellationException failure) {
                // the lock must cover completion; the failure stays in the future for vanilla
            }

            return future;
        });
    }

    /** The one locking primitive: also the synchronous scope of the FULL step body on its owning thread. */
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
