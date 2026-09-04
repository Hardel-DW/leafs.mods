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

/** Vanilla's generation on the pool: each step reserves the radius it writes, at the chunk's urgency. */
public final class GenerationSteps {
    private static final long[] NO_RESERVATION = {};

    private final ChunkPool pool;
    private final ChunkOwners owners;
    private final QueuedSteps queued = new QueuedSteps();

    public GenerationSteps(ChunkPool pool, ChunkOwners owners) {
        this.pool = pool;
        this.owners = owners;
    }

    public void run(ChunkGenerationTask task) {
        ChunkPos pos = task.getCenter().getPos();
        long center = pos.pack();
        ChunkTask driver = ChunkTask.of(owners.urgency(pos.x(), pos.z()), NO_RESERVATION, () -> {
            queued.driverStarted(center);
            CompletableFuture<?> waiting = task.runUntilWait();
            if (waiting != null) {
                waiting.thenRun(() -> run(task));
            }
        });
        queued.driverQueued(center, driver);
        pool.submit(driver);
    }

    public CompletableFuture<ChunkAccess> apply(ChunkStep step, WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        long key = pos.pack();
        int urgency = Math.min(owners.urgency(pos.x(), pos.z()), owners.urgency(cache.minX + cache.sizeX / 2, cache.minZ + cache.sizeZ / 2));
        CompletableFuture<ChunkAccess> result = new CompletableFuture<>();
        ChunkTask task = new ChunkTask(urgency, owners.area(pos.x(), pos.z(), step.blockStateWriteRadius())) {
            @Override
            protected @Nullable CompletableFuture<?> run() {
                queued.stepStarted(key);
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
        };
        queued.stepQueued(key, task);
        pool.submit(task);
        return result;
    }

    public void expedite(int chunkX, int chunkZ) {
        queued.expedite(pool, chunkX, chunkZ);
    }

    public String describeQueued(int chunkX, int chunkZ) {
        return queued.describeAround(chunkX, chunkZ);
    }

    public Executor loading(ChunkPos pos) {
        return task -> owners.onPool(pos.x(), pos.z(), 0, task);
    }
}
