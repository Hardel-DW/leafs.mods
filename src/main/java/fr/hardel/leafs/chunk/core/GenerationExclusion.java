package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.chunk.propagator.AreaLock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Spatial exclusion of generation steps, on the write radius vanilla declares for each. The area is held until the step's future completes, never by a parked worker. */
public final class GenerationExclusion {

    /** FULL runs on the owning region and takes this radius there, never on a pool worker. */
    public static final int FULL_STEP_RADIUS = 1;

    private final AreaLock lock = new AreaLock(0);

    public CompletableFuture<ChunkAccess> runStep(ChunkStep step, ChunkPos pos, Supplier<CompletableFuture<ChunkAccess>> body) {
        int radius = step.blockStateWriteRadius();
        if (radius < 0) {
            return body.get();
        }

        AreaLock.Node node = lock.lock(pos.x(), pos.z(), radius);
        CompletableFuture<ChunkAccess> future;
        try {
            future = body.get();
        } catch (RuntimeException failure) {
            lock.unlock(node);
            throw failure;
        }

        return future.whenComplete((_, _) -> lock.unlock(node));
    }

    /** The synchronous scope of the FULL step body on its owning thread. */
    public <T> T supplyExcluded(ChunkPos pos, int radius, Supplier<T> body) {
        AreaLock.Node node = lock.lock(pos.x(), pos.z(), radius);
        try {
            return body.get();
        } finally {
            lock.unlock(node);
        }
    }
}
