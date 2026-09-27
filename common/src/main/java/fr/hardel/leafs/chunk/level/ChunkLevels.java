package fr.hardel.leafs.chunk.level;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import fr.hardel.leafs.ticking.TickEpochs;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class ChunkLevels {
    private static final int STRIPES = 256;
    private static final ThreadLocal<ChunkLevels> DRAINING = new ThreadLocal<>();

    private final int none;
    private final TickEpochs epochs;
    private final ConcurrentLong2ObjectMap<Section> sections = new ConcurrentLong2ObjectMap<>();
    private final ConcurrentLinkedQueue<Section> dirty = new ConcurrentLinkedQueue<>();
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

    public ChunkLevels(int levelCount, TickEpochs epochs) {
        this.none = levelCount - 1;
        this.epochs = epochs;
        Arrays.setAll(stripes, _ -> new ReentrantLock());
    }

    public int none() {
        return none;
    }

    public void setSource(int chunkX, int chunkZ, int level) {
        long key = Section.keyOf(chunkX, chunkZ);
        long written = epochs.now();
        while (true) {
            Section section = sections.computeIfAbsent(key, k -> new Section(k, none));
            if (section.post(Section.index(chunkX, chunkZ), level, written)) {
                queue(section);
                return;
            }
        }
    }

    public int sectionCount() {
        return sections.size();
    }

    // Used by the Leafs Debug mod
    public int level(long chunkKey) {
        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        Section section = sections.get(Section.keyOf(chunkX, chunkZ));
        return section == null ? none : section.level(Section.index(chunkX, chunkZ));
    }

    public void forEachAtMost(int level, LongConsumer consumer) {
        for (Section section : sections.values()) {
            section.forEachAtMost(level, consumer);
        }
    }

    public static boolean draining() {
        return DRAINING.get() != null;
    }

    public boolean drain(LevelListener listener) {
        if (DRAINING.get() != null) {
            return false;
        }

        List<Section> taken = new ArrayList<>();
        for (Section section = dirty.poll(); section != null; section = dirty.poll()) {
            section.queued.set(false);
            taken.add(section);
        }

        if (taken.isEmpty()) {
            return false;
        }

        boolean[] held = lock(taken.stream().mapToLong(section -> section.key).toArray());
        try {
            return propagate(taken, epochs.oldestOpen(), listener);
        } finally {
            unlock(held);
        }
    }

    public <T> T settled(int chunkX, int chunkZ, LevelListener listener, Supplier<T> body) {
        long key = Section.keyOf(chunkX, chunkZ);
        boolean[] held = lock(key);
        try {
            Section center = sections.get(key);
            if (center != null && DRAINING.get() == null) {
                propagate(List.of(center), Long.MIN_VALUE, listener);
            }

            return body.get();
        } finally {
            unlock(held);
        }
    }

    private void queue(Section section) {
        if (section.queued.compareAndSet(false, true)) {
            dirty.add(section);
        }
    }

    private boolean propagate(List<Section> taken, long oldestOpen, LevelListener listener) {
        DRAINING.set(this);
        try {
            Long2ByteOpenHashMap changes = new Long2ByteOpenHashMap();
            for (Section section : taken) {
                if (section.takeVisible(oldestOpen, changes)) {
                    queue(section);
                }
            }

            boolean changed = !changes.isEmpty() && new Propagation(changes).run(listener);
            for (Section center : taken) {
                retireAround(center.key);
            }

            return changed;
        } finally {
            DRAINING.remove();
        }
    }

    private void retireAround(long centerKey) {
        int sectionX = ChunkPos.getX(centerKey);
        int sectionZ = ChunkPos.getZ(centerKey);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long key = ChunkPos.pack(sectionX + dx, sectionZ + dz);
                Section section = sections.get(key);
                if (section != null && section.retire()) {
                    sections.remove(key, section);
                }
            }
        }
    }

    // A change reaches at most one section away, so the stripes around every center cover the whole propagation.
    private boolean[] lock(long... centerKeys) {
        boolean[] held = new boolean[STRIPES];
        for (long centerKey : centerKeys) {
            int sectionX = ChunkPos.getX(centerKey);
            int sectionZ = ChunkPos.getZ(centerKey);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    held[(int) ((ChunkPos.pack(sectionX + dx, sectionZ + dz) * 0x9E3779B97F4A7C15L) >>> 56)] = true;
                }
            }
        }

        for (int stripe = 0; stripe < STRIPES; stripe++) {
            if (held[stripe]) {
                stripes[stripe].lock();
            }
        }

        return held;
    }

    private void unlock(boolean[] held) {
        for (int stripe = 0; stripe < STRIPES; stripe++) {
            if (held[stripe]) {
                stripes[stripe].unlock();
            }
        }
    }

    private final class Propagation {
        private final Long2ByteMap changes;
        private final Long2ByteLinkedOpenHashMap overlay = new Long2ByteLinkedOpenHashMap();
        private final Long2ByteLinkedOpenHashMap olds = new Long2ByteLinkedOpenHashMap();
        private final LongArrayFIFOQueue removals = new LongArrayFIFOQueue();
        private final LongArrayList[] seeds = new LongArrayList[none];

        private Propagation(Long2ByteMap changes) {
            this.changes = changes;
            overlay.defaultReturnValue((byte) -1);
        }

        private boolean run(LevelListener listener) {
            for (ObjectIterator<Long2ByteMap.Entry> iterator = changes.long2ByteEntrySet().iterator(); iterator.hasNext(); ) {
                Long2ByteMap.Entry entry = iterator.next();
                int chunkX = ChunkPos.getX(entry.getLongKey());
                int chunkZ = ChunkPos.getZ(entry.getLongKey());
                sectionOf(chunkX, chunkZ).setSource(Section.index(chunkX, chunkZ), entry.getByteValue());
                changeSource(chunkX, chunkZ, entry.getByteValue());
            }

            lower();
            raise();
            return publish(listener);
        }

        private void changeSource(int chunkX, int chunkZ, int source) {
            int current = level(chunkX, chunkZ);
            if (source < current) {
                seed(chunkX, chunkZ, source);
            } else if (source > current) {
                remove(chunkX, chunkZ, current);
            }
        }

        private void lower() {
            while (!removals.isEmpty()) {
                long key = removals.dequeueLong();
                int chunkX = ChunkPos.getX(key);
                int chunkZ = ChunkPos.getZ(key);
                int old = olds.get(key);
                int source = source(chunkX, chunkZ);
                if (source != none) {
                    seed(chunkX, chunkZ, source);
                }

                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }

                        int neighbour = level(chunkX + dx, chunkZ + dz);
                        if (neighbour == none) {
                            continue;
                        }

                        if (neighbour == old + 1) {
                            remove(chunkX + dx, chunkZ + dz, neighbour);
                        } else if (neighbour <= old) {
                            seed(chunkX + dx, chunkZ + dz, neighbour);
                        }
                    }
                }
            }
        }

        private void raise() {
            for (int level = 0; level < none; level++) {
                LongArrayList atLevel = seeds[level];
                if (atLevel == null) {
                    continue;
                }

                for (int index = 0; index < atLevel.size(); index++) {
                    long key = atLevel.getLong(index);
                    int chunkX = ChunkPos.getX(key);
                    int chunkZ = ChunkPos.getZ(key);
                    int current = level(chunkX, chunkZ);
                    if (current < level) {
                        continue;
                    }

                    if (current > level) {
                        if (source(chunkX, chunkZ) != level) {
                            continue;
                        }

                        overlay.put(key, (byte) level);
                    }

                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if ((dx != 0 || dz != 0) && level(chunkX + dx, chunkZ + dz) > level + 1) {
                                push(chunkX + dx, chunkZ + dz, level + 1);
                            }
                        }
                    }
                }
            }
        }

        private void remove(int chunkX, int chunkZ, int current) {
            long key = ChunkPos.pack(chunkX, chunkZ);
            olds.putIfAbsent(key, (byte) current);
            overlay.put(key, (byte) none);
            removals.enqueue(key);
        }

        private void seed(int chunkX, int chunkZ, int level) {
            long key = ChunkPos.pack(chunkX, chunkZ);
            olds.putIfAbsent(key, (byte) level(chunkX, chunkZ));
            LongArrayList atLevel = seeds[level];
            if (atLevel == null) {
                atLevel = seeds[level] = new LongArrayList();
            }

            atLevel.add(key);
        }

        private void push(int chunkX, int chunkZ, int level) {
            seed(chunkX, chunkZ, level);
            overlay.put(ChunkPos.pack(chunkX, chunkZ), (byte) level);
        }

        private int level(int chunkX, int chunkZ) {
            int overlaid = overlay.get(ChunkPos.pack(chunkX, chunkZ));
            if (overlaid != -1) {
                return overlaid;
            }

            Section section = sections.get(Section.keyOf(chunkX, chunkZ));
            return section == null ? none : section.level(Section.index(chunkX, chunkZ));
        }

        private int source(int chunkX, int chunkZ) {
            Section section = sections.get(Section.keyOf(chunkX, chunkZ));
            return section == null ? none : section.source(Section.index(chunkX, chunkZ));
        }

        private Section sectionOf(int chunkX, int chunkZ) {
            return sections.computeIfAbsent(Section.keyOf(chunkX, chunkZ), key -> new Section(key, none));
        }

        private boolean publish(LevelListener listener) {
            boolean changed = false;
            for (ObjectIterator<Long2ByteMap.Entry> iterator = overlay.long2ByteEntrySet().fastIterator(); iterator.hasNext(); ) {
                Long2ByteMap.Entry entry = iterator.next();
                long key = entry.getLongKey();
                int old = olds.get(key);
                int settled = entry.getByteValue();
                if (settled == old) {
                    continue;
                }

                int chunkX = ChunkPos.getX(key);
                int chunkZ = ChunkPos.getZ(key);
                sectionOf(chunkX, chunkZ).publish(Section.index(chunkX, chunkZ), settled);
                listener.changed(key, old, settled);
                changed = true;
            }

            if (changed) {
                listener.published();
            }

            return changed;
        }
    }
}
