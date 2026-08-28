package fr.hardel.leafs.chunk;

import fr.hardel.leafs.region.Region;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.function.LongPredicate;

/** One mail queue per chunk, drained by whichever region owns the chunk at its tick start, by the server thread for a chunk no region owns. Every post holds what its mail needs until it ran, its chunk at least, so a holder exists to drain it. */
public final class ChunkMailbox {
    private final ChunkHoldController holds;
    private final Long2ObjectOpenHashMap<ArrayDeque<Mail>> mail = new Long2ObjectOpenHashMap<>();
    private final EnumMap<MailHold.Level, Long2IntOpenHashMap> heldChunks = new EnumMap<>(MailHold.Level.class);
    private volatile int pending;

    private record Mail(int chunkX, int chunkZ, MailHold hold, Runnable task) {}

    public ChunkMailbox(ChunkHoldController holds) {
        this.holds = holds;
        for (MailHold.Level level : MailHold.Level.values()) {
            heldChunks.put(level, new Long2IntOpenHashMap());
        }
    }

    public void post(int chunkX, int chunkZ, Runnable task) {
        post(chunkX, chunkZ, MailHold.CHUNK, task);
    }

    public synchronized void post(int chunkX, int chunkZ, MailHold hold, Runnable task) {
        Mail posted = new Mail(chunkX, chunkZ, hold, task);
        forEachHeld(posted, (x, z) -> {
            if (heldChunks.get(hold.level()).addTo(ChunkPos.pack(x, z), 1) == 0) {
                holds.addHold(x, z, hold.level());
            }
        });

        mail.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), _ -> new ArrayDeque<>()).addLast(posted);
        pending++;
    }

    /** The owner's pass over its photo. Mail is popped one by one: a throw leaves the ones behind it queued, holds intact. */
    public int drain(List<ChunkHolder> holders) {
        if (pending == 0) {
            return 0;
        }

        int ran = 0;
        for (ChunkHolder holder : holders) {
            ran += drainChunk(holder.getPos().pack());
        }

        return ran;
    }

    /** A borrowed region's chunks, for the server thread that holds it. */
    public int drain(Region<?> region, ChunkMap chunkMap) {
        if (pending == 0) {
            return 0;
        }

        LongList keys = new LongArrayList();
        region.forEachChunk((chunkX, chunkZ) -> keys.add(ChunkPos.pack(chunkX, chunkZ)));
        int ran = 0;
        for (long key : keys) {
            if (chunkMap.getVisibleChunkIfPresent(key) != null) {
                ran += drainChunk(key);
            }
        }

        return ran;
    }

    /** The chunks no region owns, for the server thread. */
    public int drainOrphans(LongPredicate orphan) {
        if (pending == 0) {
            return 0;
        }

        int ran = 0;
        for (long key : keys()) {
            if (orphan.test(key)) {
                ran += drainChunk(key);
            }
        }

        return ran;
    }

    /** Every chunk, for the thread that owns them all: the shutdown, or the server thread pumping while nothing else may run. */
    public int drainAll() {
        int ran = 0;
        for (long key : keys()) {
            ran += drainChunk(key);
        }

        return ran;
    }

    public int size() {
        return pending;
    }

    /** Bounded by the mail present at the start, so a task that re-posts to its own chunk waits for the next pass. */
    private int drainChunk(long key) {
        int budget = queued(key);
        int ran = 0;
        Mail next;
        while (ran < budget && (next = poll(key)) != null) {
            ran++;
            try {
                next.task().run();
            } finally {
                release(next);
            }
        }

        return ran;
    }

    private synchronized int queued(long key) {
        ArrayDeque<Mail> queue = mail.get(key);
        return queue == null ? 0 : queue.size();
    }

    private synchronized Mail poll(long key) {
        ArrayDeque<Mail> queue = mail.get(key);
        if (queue == null) {
            return null;
        }

        Mail next = queue.pollFirst();
        if (queue.isEmpty()) {
            mail.remove(key);
        }

        return next;
    }

    private synchronized void release(Mail ran) {
        pending--;
        Long2IntOpenHashMap held = heldChunks.get(ran.hold().level());
        forEachHeld(ran, (x, z) -> {
            long key = ChunkPos.pack(x, z);
            if (held.addTo(key, -1) == 1) {
                held.remove(key);
                holds.removeHold(x, z, ran.hold().level());
            }
        });
    }

    private static void forEachHeld(Mail mail, Region.ChunkConsumer consumer) {
        int radius = mail.hold().radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                consumer.accept(mail.chunkX() + dx, mail.chunkZ() + dz);
            }
        }
    }

    private synchronized long[] keys() {
        return mail.keySet().toLongArray();
    }
}
