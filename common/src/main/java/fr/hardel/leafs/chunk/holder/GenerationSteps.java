package fr.hardel.leafs.chunk.holder;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.chunk.pool.ChunkTask.Kind;
import net.minecraft.CrashReport;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class GenerationSteps {
    private static final int STATUSES = ChunkStatus.getStatusList().size();
    private static final RuntimeException CANCELLED = new RuntimeException("Step cancelled in the queue", null, false, false) {
    };

    private final ChunkMap chunkMap;
    private final ChunkPool pool;
    private final ChunkOwners owners;
    private final ConcurrentLong2ObjectMap<StepTask[]> queued = new ConcurrentLong2ObjectMap<>();

    public GenerationSteps(ChunkMap chunkMap, ChunkPool pool, ChunkOwners owners) {
        this.chunkMap = chunkMap;
        this.pool = pool;
        this.owners = owners;
    }

    public void run(ChunkGenerationTask task) {
        ChunkPos pos = task.getCenter().getPos();
        pool.submit(ChunkTask.of(Kind.STEP, owners.place(pos.x(), pos.z(), pos.x(), pos.z()), ChunkTask.NO_RESERVATION, () -> drive(task)));
    }

    private void drive(ChunkGenerationTask task) {
        CompletableFuture<?> waiting = task.runUntilWait();
        if (waiting != null) {
            waiting.thenRun(() -> pool.execute(() -> drive(task)));
        }
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> applyOnHolder(GenerationChunkHolder holder, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache) {
        ChunkStatus status = step.targetStatus();
        if (holder.isStatusDisallowed(status)) {
            return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
        }

        if (!holder.acquireStatusBump(status)) {
            return holder.getOrCreateFuture(status);
        }

        return chunkMap.applyStep(holder, step, cache).handle((chunk, failure) -> {
            Throwable cause = failure instanceof CompletionException wrapped ? wrapped.getCause() : failure;
            if (cause == CANCELLED) {
                holder.startedWork.compareAndSet(status, status == ChunkStatus.EMPTY ? null : status.getParent());
                return GenerationChunkHolder.UNLOADED_CHUNK;
            }

            if (cause != null) {
                BlockableEventLoop.relayDelayCrash(CrashReport.forThrowable(cause, "Exception chunk generation/loading"));
            } else {
                holder.completeFuture(status, chunk);
            }

            return ChunkResult.of(chunk);
        });
    }

    public CompletableFuture<ChunkAccess> apply(ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, Supplier<CompletableFuture<ChunkAccess>> body) {
        ChunkPos pos = chunk.getPos();
        ChunkTask.Place place = owners.place(pos.x(), pos.z(), cache.minX + cache.sizeX / 2, cache.minZ + cache.sizeZ / 2);
        StepTask task = new StepTask(place, owners.area(Kind.STEP, pos.x(), pos.z(), step.blockStateWriteRadius()), step, chunk, body);
        queued.compute(pos.pack(), (_, slots) -> {
            StepTask[] target = slots == null ? new StepTask[STATUSES] : slots;
            target[step.targetStatus().getIndex()] = task;
            return target;
        });
        pool.submit(task);
        return task.result;
    }

    public void cancelDisallowed(GenerationChunkHolder holder) {
        StepTask[] slots = queued.get(holder.getPos().pack());
        if (slots == null) {
            return;
        }

        for (StepTask task : slots) {
            if (task != null && holder.isStatusDisallowed(task.step.targetStatus())) {
                task.cancel();
            }
        }
    }

    public Executor loading(ChunkPos pos) {
        return task -> owners.onPool(Kind.STEP, pos.x(), pos.z(), 0, task);
    }

    private void forget(StepTask task) {
        queued.compute(task.chunk.getPos().pack(), (_, slots) -> {
            if (slots == null) {
                return null;
            }

            int index = task.step.targetStatus().getIndex();
            if (slots[index] == task) {
                slots[index] = null;
            }

            for (StepTask slot : slots) {
                if (slot != null) {
                    return slots;
                }
            }

            return null;
        });
    }

    private final class StepTask extends ChunkTask {
        private final ChunkStep step;
        private final ChunkAccess chunk;
        private final Supplier<CompletableFuture<ChunkAccess>> body;
        private final CompletableFuture<ChunkAccess> result = new CompletableFuture<>();
        private final AtomicBoolean taken = new AtomicBoolean();

        private StepTask(Place place, long[] reserved, ChunkStep step, ChunkAccess chunk, Supplier<CompletableFuture<ChunkAccess>> body) {
            super(Kind.STEP, place, reserved);
            this.step = step;
            this.chunk = chunk;
            this.body = body;
        }

        @Override
        protected @Nullable CompletableFuture<?> run() {
            if (!taken.compareAndSet(false, true)) {
                return null;
            }

            forget(this);
            CompletableFuture<ChunkAccess> applied;
            try {
                applied = body.get();
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

        private void cancel() {
            if (taken.compareAndSet(false, true)) {
                forget(this);
                withdraw();
                result.completeExceptionally(CANCELLED);
            }
        }

        private void fail(Throwable failure) {
            Leafs.LOGGER.error("Step {} of chunk {} failed", step.targetStatus(), chunk.getPos(), failure);
            result.completeExceptionally(failure);
        }
    }
}
