package fr.hardel.leafs.chunk.holder;

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

/** Vanilla's generation on the pool: the task advances layer by layer, each step reserves the radius it writes, everything at the chunk's urgency. */
public final class GenerationSteps {
    private final ChunkPool pool;
    private final ChunkOwners owners;

    public GenerationSteps(ChunkPool pool, ChunkOwners owners) {
        this.pool = pool;
        this.owners = owners;
    }

    /** The task schedules a layer, waits for it off any thread, and comes back here. */
    public void run(ChunkGenerationTask task) {
        ChunkPos pos = task.getCenter().getPos();
        owners.onPool(pos.x(), pos.z(), -1, () -> {
            CompletableFuture<?> waiting = task.runUntilWait();
            if (waiting != null) {
                waiting.thenRun(() -> run(task));
            }
        });
    }

    /** One step of one chunk, as urgent as the chunk that asked for it, its future completing when the step's own future does. */
    public CompletableFuture<ChunkAccess> apply(ChunkStep step, WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int urgency = Math.min(owners.urgency(pos.x(), pos.z()), owners.urgency(cache.minX + cache.sizeX / 2, cache.minZ + cache.sizeZ / 2));
        CompletableFuture<ChunkAccess> result = new CompletableFuture<>();
        pool.submit(new ChunkTask(urgency, owners.area(pos.x(), pos.z(), step.blockStateWriteRadius())) {
            @Override
            protected @Nullable CompletableFuture<?> run() {
                CompletableFuture<ChunkAccess> applied;
                try {
                    applied = step.apply(context, cache, chunk);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                    return null;
                }

                applied.whenComplete((generated, failure) -> {
                    if (failure == null) {
                        result.complete(generated);
                    } else {
                        result.completeExceptionally(failure);
                    }
                });
                return applied;
            }
        });
        return result;
    }

    /** The read of a chunk file, once the disk thread hands it over. */
    public Executor loading(ChunkPos pos) {
        return task -> owners.onPool(pos.x(), pos.z(), 0, task);
    }
}
