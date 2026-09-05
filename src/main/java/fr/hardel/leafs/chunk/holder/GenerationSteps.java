package fr.hardel.leafs.chunk.holder;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Vanilla's generation on the pool: each step reserves the radius it writes, placed at the chunk it writes and the centre it serves. */
public final class GenerationSteps {
    private static final long[] NO_RESERVATION = {};

    private final ChunkPool pool;
    private final ChunkOwners owners;

    public GenerationSteps(ChunkPool pool, ChunkOwners owners) {
        this.pool = pool;
        this.owners = owners;
    }

    /** A new task starts at the urgency of its centre. */
    public void run(ChunkGenerationTask task) {
        ChunkPos pos = task.getCenter().getPos();
        pool.submit(ChunkTask.of(owners.place(pos.x(), pos.z(), pos.x(), pos.z()), NO_RESERVATION, () -> drive(task)));
    }

    /** A layer done, the task schedules the next one or releases its claims on 289 holders: a continuation, so it heads the pool like every continuation. */
    private void drive(ChunkGenerationTask task) {
        CompletableFuture<?> waiting = task.runUntilWait();
        if (waiting != null) {
            waiting.thenRun(() -> pool.execute(() -> drive(task)));
        }
    }

    public CompletableFuture<ChunkAccess> apply(ChunkStep step, WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkTask.Place place = owners.place(pos.x(), pos.z(), cache.minX + cache.sizeX / 2, cache.minZ + cache.sizeZ / 2);
        CompletableFuture<ChunkAccess> result = new CompletableFuture<>();
        pool.submit(new ChunkTask(place, owners.area(pos.x(), pos.z(), step.blockStateWriteRadius())) {
            @Override
            protected @Nullable CompletableFuture<?> run() {
                CompletableFuture<ChunkAccess> applied;
                try {
                    applied = step.apply(context, cache, chunk);
                } catch (Throwable failure) {
                    fail(failure);
                    return null;
                }

                applied.whenComplete((generated, failure) -> {
                    if (failure == null) {
                        result.complete(generated);
                    } else {
                        fail(failure);
                    }
                });
                return applied;
            }

            /** Vanilla keeps a failed step for the server thread's next loop, which may be the one waiting: logged here. */
            private void fail(Throwable failure) {
                Leafs.LOGGER.error("Step {} of chunk {} failed", step.targetStatus(), pos, failure);
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    public Executor loading(ChunkPos pos) {
        return task -> owners.onPool(pos.x(), pos.z(), 0, task);
    }
}
