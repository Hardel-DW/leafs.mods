package fr.hardel.leafs.chunk.pool;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;

/** A unit of chunk work. A returned future keeps the reservation until it completes. */
public abstract class ChunkTask {
    static final int UNQUEUED = -1;
    private static final VarHandle BUCKET;

    static {
        try {
            BUCKET = MethodHandles.lookup().findVarHandle(ChunkTask.class, "bucket", int.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final long[] reserved;
    private volatile int priority;
    private volatile int bucket = UNQUEUED;

    protected ChunkTask(int priority, long... reserved) {
        this.priority = priority;
        this.reserved = reserved;
    }

    public static ChunkTask of(int priority, long[] reserved, Runnable body) {
        return new ChunkTask(priority, reserved) {
            @Override
            protected @Nullable CompletableFuture<?> run() {
                body.run();
                return null;
            }
        };
    }

    /** Chunk coordinates fit in 22 bits each, the owner in the 20 above, so two levels never share a key. */
    public static long key(int owner, int chunkX, int chunkZ) {
        return ((long) owner << 44) | ((chunkX & 0x3FFFFFL) << 22) | (chunkZ & 0x3FFFFFL);
    }

    public final long[] reserved() {
        return reserved;
    }

    public final int priority() {
        return priority;
    }

    /** Null when the work is done on return. */
    protected abstract @Nullable CompletableFuture<?> run();

    final void wants(int priority) {
        this.priority = priority;
    }

    final void enqueuedAt(int bucket) {
        if (!BUCKET.compareAndSet(this, UNQUEUED, bucket)) {
            throw new IllegalStateException("Task already queued at bucket " + this.bucket);
        }
    }

    /** False for a stale entry a move left behind. */
    final boolean claimAt(int bucket) {
        return BUCKET.compareAndSet(this, bucket, UNQUEUED);
    }

    final boolean moveTo(int bucket) {
        while (true) {
            int current = this.bucket;
            if (current == UNQUEUED || current == bucket) {
                return false;
            }

            if (BUCKET.compareAndSet(this, current, bucket)) {
                return true;
            }
        }
    }
}
