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

    /** Where a task works, in reservation keys: the chunk it writes and the centre it serves. The more urgent of the two is its priority, so a dependency inherits the urgency of its user. */
    public record Place(long chunkKey, long centerKey, Urgency urgency) {
        public int priority() {
            int chunk = urgency.of(chunkX(chunkKey), chunkZ(chunkKey));
            return chunkKey == centerKey ? chunk : Math.min(chunk, urgency.of(chunkX(centerKey), chunkZ(centerKey)));
        }
    }

    private final long[] reserved;
    private final @Nullable Place place;
    private volatile int priority;
    private volatile int bucket = UNQUEUED;
    private volatile boolean withdrawn;

    /** Housekeeping with a fixed priority, invisible to the re-prioritisation. */
    protected ChunkTask(int priority, long... reserved) {
        this.place = null;
        this.priority = priority;
        this.reserved = reserved;
    }

    protected ChunkTask(Place place, long... reserved) {
        this.place = place;
        this.priority = place.priority();
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

    public static ChunkTask of(Place place, long[] reserved, Runnable body) {
        return new ChunkTask(place, reserved) {
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

    public static int chunkX(long key) {
        return (int) (key << 20 >> 42);
    }

    public static int chunkZ(long key) {
        return (int) (key << 42 >> 42);
    }

    public final long[] reserved() {
        return reserved;
    }

    public final @Nullable Place place() {
        return place;
    }

    public final int priority() {
        return priority;
    }

    /** Null when the work is done on return. */
    protected abstract @Nullable CompletableFuture<?> run();

    /** Left the queue before running: the pool drops it when it reaches it. */
    protected final void withdraw() {
        withdrawn = true;
    }

    final boolean withdrawn() {
        return withdrawn;
    }

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
