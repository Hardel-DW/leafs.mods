package fr.hardel.leafs.chunk.level;

import fr.hardel.excess.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.shorts.Short2ByteMap;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class ChunkLevels {
    private static final int STRIPES = 256;
    private static final ThreadLocal<ChunkLevels> DRAINING = new ThreadLocal<>();

    private final int none;
    private final ConcurrentLong2ObjectMap<Section> sections = new ConcurrentLong2ObjectMap<>();
    private final ConcurrentLinkedQueue<Section> dirty = new ConcurrentLinkedQueue<>();
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

    public ChunkLevels(int levelCount) {
        this.none = levelCount - 1;
        Arrays.setAll(stripes, _ -> new ReentrantLock());
    }

    public int none() {
        return none;
    }

    public void setSource(int chunkX, int chunkZ, int level) {
        long key = Section.keyOf(chunkX, chunkZ);
        while (true) {
            Section section = sections.computeIfAbsent(key, k -> new Section(k, none));
            if (!section.post(Section.index(chunkX, chunkZ), level)) {
                continue;
            }

            if (section.queued.compareAndSet(false, true)) {
                dirty.add(section);
            }

            return;
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

        DRAINING.set(this);
        try {
            boolean changed = false;
            Section section;
            while ((section = dirty.poll()) != null) {
                section.queued.set(false);
                changed |= drainSection(section, listener);
            }

            return changed;
        } finally {
            DRAINING.remove();
        }
    }

    public <T> T settled(int chunkX, int chunkZ, LevelListener listener, Supplier<T> body) {
        long key = Section.keyOf(chunkX, chunkZ);
        int[] taken = lock(key);
        try {
            if (DRAINING.get() == null) {
                DRAINING.set(this);
                try {
                    propagate(sections.get(key), listener);
                } finally {
                    DRAINING.remove();
                }
            }

            return body.get();
        } finally {
            unlock(taken);
        }
    }

    private boolean drainSection(Section center, LevelListener listener) {
        int[] taken = lock(center.key);
        try {
            return propagate(center, listener);
        } finally {
            unlock(taken);
        }
    }

    private boolean propagate(@Nullable Section center, LevelListener listener) {
        if (center == null) {
            return false;
        }

        Short2ByteMap batch = center.takePending();
        boolean changed = !batch.isEmpty() && new Propagation(center, batch).run(listener);
        int sectionX = ChunkPos.getX(center.key);
        int sectionZ = ChunkPos.getZ(center.key);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long key = ChunkPos.pack(sectionX + dx, sectionZ + dz);
                Section section = sections.get(key);
                if (section != null && section.retire()) {
                    sections.remove(key, section);
                }
            }
        }

        return changed;
    }

    private int[] lock(long centerKey) {
        int sectionX = ChunkPos.getX(centerKey);
        int sectionZ = ChunkPos.getZ(centerKey);
        int[] taken = new int[9];
        int count = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                taken[count++] = (int) ((ChunkPos.pack(sectionX + dx, sectionZ + dz) * 0x9E3779B97F4A7C15L) >>> 56);
            }
        }

        Arrays.sort(taken);
        for (int stripe : taken) {
            stripes[stripe].lock();
        }

        return taken;
    }

    private void unlock(int[] taken) {
        for (int index = taken.length - 1; index >= 0; index--) {
            stripes[taken[index]].unlock();
        }
    }

    private final class Propagation {
        private final Section center;
        private final Short2ByteMap batch;
        private final Long2ByteLinkedOpenHashMap overlay = new Long2ByteLinkedOpenHashMap();
        private final Long2ByteLinkedOpenHashMap olds = new Long2ByteLinkedOpenHashMap();
        private final LongArrayFIFOQueue removals = new LongArrayFIFOQueue();
        private final LongArrayList[] seeds = new LongArrayList[none];

        private Propagation(Section center, Short2ByteMap batch) {
            this.center = center;
            this.batch = batch;
            overlay.defaultReturnValue((byte) -1);
        }

        private boolean run(LevelListener listener) {
            int originX = ChunkPos.getX(center.key) << Section.SHIFT;
            int originZ = ChunkPos.getZ(center.key) << Section.SHIFT;
            for (ObjectIterator<Short2ByteMap.Entry> iterator = batch.short2ByteEntrySet().iterator(); iterator.hasNext(); ) {
                Short2ByteMap.Entry entry = iterator.next();
                int index = entry.getShortKey() & 0xFFFF;
                center.setSource(index, entry.getByteValue());
                changeSource(originX + (index & Section.MASK), originZ + (index >> Section.SHIFT), entry.getByteValue());
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
