package fr.hardel.leafs.chunk.pool;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;

public abstract class ChunkTask {
    public static final long[] NO_RESERVATION = {};
    static final int UNQUEUED = -1;
    private static final VarHandle BUCKET;

    static {
        try {
            BUCKET = MethodHandles.lookup().findVarHandle(ChunkTask.class, "bucket", int.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public enum Kind {
        STEP,
        LIGHT,
        OWNER,
        HOUSEKEEPING
    }

    public record Place(long chunkKey, long centerKey, Urgency urgency) {
        public int priority() {
            int chunk = urgency.of(chunkX(chunkKey), chunkZ(chunkKey));
            return chunkKey == centerKey ? chunk : Math.min(chunk, urgency.of(chunkX(centerKey), chunkZ(centerKey)));
        }
    }

    private final Kind kind;
    private final long[] reserved;
    private final @Nullable Place place;
    private volatile int priority;
    private volatile int bucket = UNQUEUED;
    private volatile boolean withdrawn;

    protected ChunkTask(Kind kind, int priority, long... reserved) {
        this.kind = kind;
        this.place = null;
        this.priority = priority;
        this.reserved = reserved;
    }

    protected ChunkTask(Kind kind, Place place, long... reserved) {
        this.kind = kind;
        this.place = place;
        this.priority = place.priority();
        this.reserved = reserved;
    }

    public static ChunkTask of(Kind kind, int priority, long[] reserved, Runnable body) {
        return new ChunkTask(kind, priority, reserved) {
            @Override
            protected @Nullable CompletableFuture<?> run() {
                body.run();
                return null;
            }
        };
    }

    public static ChunkTask of(Kind kind, Place place, long[] reserved, Runnable body) {
        return new ChunkTask(kind, place, reserved) {
            @Override
            protected @Nullable CompletableFuture<?> run() {
                body.run();
                return null;
            }
        };
    }

    public static long key(int owner, int chunkX, int chunkZ) {
        return ((long) owner << 44) | ((chunkX & 0x3FFFFFL) << 22) | (chunkZ & 0x3FFFFFL);
    }

    public static int chunkX(long key) {
        return (int) (key << 20 >> 42);
    }

    public static int chunkZ(long key) {
        return (int) (key << 42 >> 42);
    }

    public final Kind kind() {
        return kind;
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

    protected abstract @Nullable CompletableFuture<?> run();

    @Override
    public String toString() {
        return place == null ? "%s at priority %d".formatted(kind, priority) : "%s at [%d, %d] priority %d".formatted(kind, chunkX(place.chunkKey()), chunkZ(place.chunkKey()), priority);
    }

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
