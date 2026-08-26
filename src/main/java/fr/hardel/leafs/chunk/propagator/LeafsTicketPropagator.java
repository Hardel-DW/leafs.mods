package fr.hardel.leafs.chunk.propagator;

import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.server.level.ChunkLevel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

/** Ticket level propagation by 64x64 sections. Levels are inverted from vanilla (a source is high, decays to zero). Sources need the ticket cell, drains lock their own 3x3 area. */
public abstract class LeafsTicketPropagator {

    public static final int SECTION_SHIFT = 6;
    private static final int SECTION_SIZE = 1 << SECTION_SHIFT;
    private static final int LEVEL_BITS = SECTION_SHIFT;
    private static final int LEVEL_COUNT = 1 << LEVEL_BITS;
    // 62 not 63: keeps every wavefront inside the 3x3 section area
    public static final int MAX_SOURCE_LEVEL = SECTION_SIZE - 2;

    private final UpdateQueue updateQueue = new UpdateQueue();
    private final ConcurrentHashMap<Long, Section> sections = new ConcurrentHashMap<>();

    /** Vanilla ticket level to inverted propagator level and back, the mapping is its own inverse. */
    public static int convertBetweenTicketLevels(int level) {
        return ChunkLevel.MAX_LEVEL + 1 - level;
    }

    /** Same packing as ChunkPos.pack, keys of the update callback decode with ChunkPos. */
    static long positionKey(int posX, int posZ) {
        return (posX & 0xFFFFFFFFL) | ((posZ & 0xFFFFFFFFL) << 32);
    }

    /** Caller must hold the ticket area cell containing (posX, posZ). */
    public void setSource(int posX, int posZ, int level) {
        if (level < 1 || level > MAX_SOURCE_LEVEL) {
            throw new IllegalArgumentException("Source level out of range: " + level);
        }

        int sectionX = posX >> SECTION_SHIFT;
        int sectionZ = posZ >> SECTION_SHIFT;
        Section section = sections.computeIfAbsent(positionKey(sectionX, sectionZ), key -> new Section(sectionX, sectionZ));
        short localIndex = localIndex(posX, posZ);
        int currentSource = (section.levels[localIndex] >>> 8) & 0xFF;

        if (currentSource == level) {
            section.queuedSources.replace(localIndex, (byte) level);
            return;
        }

        stageSource(section, localIndex, (byte) level);
    }

    /** Caller must hold the ticket area cell containing (posX, posZ). */
    public void removeSource(int posX, int posZ) {
        Section section = sections.get(positionKey(posX >> SECTION_SHIFT, posZ >> SECTION_SHIFT));
        if (section == null) {
            return;
        }

        short localIndex = localIndex(posX, posZ);
        int currentSource = (section.levels[localIndex] >>> 8) & 0xFF;

        if (currentSource == 0) {
            section.queuedSources.replace(localIndex, (byte) 0);
            return;
        }

        stageSource(section, localIndex, (byte) 0);
    }

    private void stageSource(Section section, short localIndex, byte level) {
        if (section.queuedSources.put(localIndex, level) == Section.NO_QUEUED_UPDATE && section.queuedSources.size() == 1) {
            updateQueue.append(new UpdateQueue.Node(section));
        }
    }

    private static short localIndex(int posX, int posZ) {
        return (short) ((posX & (SECTION_SIZE - 1)) | ((posZ & (SECTION_SIZE - 1)) << SECTION_SHIFT));
    }

    /** New levels per chunk, zero means gone. Called under the 3x3 area, possibly concurrently for far sections; the map is reused. */
    protected abstract void onLevelUpdates(Long2ByteLinkedOpenHashMap updates);

    /** Called after each drained section released its locks: work built under them starts here. */
    protected void onSectionDrained() {
    }

    /** Drains everything staged before the call, shared with concurrent drainers. Caller must not hold a ticket cell. */
    public void performUpdates(AreaLock ticketLock) {
        if (updateQueue.peek() == null) {
            return;
        }

        long maxOrder = updateQueue.getLastOrder();
        Wavefront wavefront = null;

        for (;;) {
            UpdateQueue.Node node = updateQueue.acquireNextOrWait(maxOrder);
            if (node == null) {
                if (!updateQueue.hasRemainingUpdates(maxOrder)) {
                    if (wavefront != null) {
                        Wavefront.release(wavefront);
                    }
                    return;
                }
                continue;
            }

            if (wavefront == null) {
                wavefront = Wavefront.acquire();
            }
            performUpdate(node.section, node, wavefront, ticketLock);
        }
    }

    /** The outer finally frees the node and fires the hook on every exit, or parked drainers never wake and work built under the locks never starts. */
    private void performUpdate(Section section, UpdateQueue.Node node, Wavefront wavefront, AreaLock ticketLock) {
        int sectionX = section.sectionX;
        int sectionZ = section.sectionZ;
        wavefront.setupEncodeOffset(sectionX, sectionZ);

        try {
            AreaLock.Node areaNode = ticketLock == null ? null: ticketLock.lock((sectionX - 1) << SECTION_SHIFT, (sectionZ - 1) << SECTION_SHIFT,
                ((sectionX + 1) << SECTION_SHIFT) | (SECTION_SIZE - 1), ((sectionZ + 1) << SECTION_SHIFT) | (SECTION_SIZE - 1));

            try {
                if (section != sections.get(positionKey(sectionX, sectionZ)))
                    return;

                int oldSourceCount = section.sources.size();
                applyQueuedSources(section, wavefront);
                int newSourceCount = section.sources.size();

                if (oldSourceCount == 0 && newSourceCount != 0) {
                    initialiseNeighbours(sectionX, sectionZ);
                }

                if (wavefront.hasQueuedSeeds()) {
                    wavefront.setupCaches(this, sectionX, sectionZ);
                    wavefront.performDecrease();
                    wavefront.destroyCaches();
                }

                if (newSourceCount == 0) {
                    releaseNeighbours(sectionX, sectionZ, oldSourceCount != 0);
                }

                if (!wavefront.updatedPositions.isEmpty()) {
                    onLevelUpdates(wavefront.updatedPositions);
                    wavefront.updatedPositions.clear();
                }
            } finally {
                if (areaNode != null) {
                    ticketLock.unlock(areaNode);
                }
            }
        } finally {
            updateQueue.remove(node);
            onSectionDrained();
        }
    }

    private void applyQueuedSources(Section section, Wavefront wavefront) {
        int sectionX = section.sectionX;
        int sectionZ = section.sectionZ;

        for (Iterator<Short2ByteMap.Entry> iterator = section.queuedSources.short2ByteEntrySet().fastIterator(); iterator.hasNext(); ) {
            Short2ByteMap.Entry entry = iterator.next();
            int localIndex = entry.getShortKey();
            int posX = (localIndex & (SECTION_SIZE - 1)) | (sectionX << SECTION_SHIFT);
            int posZ = ((localIndex >> SECTION_SHIFT) & (SECTION_SIZE - 1)) | (sectionZ << SECTION_SHIFT);
            int newSource = entry.getByteValue();

            short stored = section.levels[localIndex];
            int currentLevel = stored & 0xFF;
            int previousSource = (stored >>> 8) & 0xFF;

            if (previousSource == newSource) {
                continue;
            }

            if ((previousSource < currentLevel && newSource <= currentLevel) || newSource == currentLevel) {
                section.levels[localIndex] = (short) (currentLevel | (newSource << 8));
            } else {
                section.levels[localIndex] = (short) (newSource | (newSource << 8));
                wavefront.updatedPositions.put(positionKey(posX, posZ), (byte) newSource);
                if (newSource != 0) {
                    wavefront.queueIncreaseSeed(posX, posZ, newSource);
                }
                if (newSource < currentLevel) {
                    wavefront.queueDecreaseSeed(posX, posZ, currentLevel);
                }
            }

            if (newSource == 0) {
                section.sources.remove((short) localIndex);
            } else if (previousSource == 0) {
                section.sources.add((short) localIndex);
            }
        }

        section.queuedSources.clear();
    }

    /** First source in a section: materialise its eight neighbours so the wavefronts never miss storage. */
    private void initialiseNeighbours(int sectionX, int sectionZ) {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                if ((dx | dz) == 0) {
                    continue;
                }

                int neighbourX = sectionX + dx;
                int neighbourZ = sectionZ + dz;
                Section neighbour = sections.computeIfAbsent(positionKey(neighbourX, neighbourZ), key -> new Section(neighbourX, neighbourZ));
                ++neighbour.neighboursWithSources;
            }
        }
    }

    /** Last source left the section: drop it and every neighbour that no longer serves any source. */
    private void releaseNeighbours(int sectionX, int sectionZ, boolean decrementNeighbours) {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                long key = positionKey(sectionX + dx, sectionZ + dz);
                Section neighbour = sections.get(key);

                if (neighbour == null) {
                    if (!decrementNeighbours && (dx | dz) != 0) {
                        continue;
                    }
                    throw new IllegalStateException("Missing section next to a section that held sources");
                }

                if (decrementNeighbours && (dx | dz) != 0) {
                    --neighbour.neighboursWithSources;
                }

                if (neighbour.neighboursWithSources == 0 && neighbour.queuedSources.isEmpty() && neighbour.sources.isEmpty()) {
                    sections.remove(key);
                }
            }
        }
    }

    private static final class Section {
        static final byte NO_QUEUED_UPDATE = (byte) -1;

        final int sectionX;
        final int sectionZ;
        final short[] levels = new short[SECTION_SIZE * SECTION_SIZE];
        final ShortOpenHashSet sources = new ShortOpenHashSet();
        final Short2ByteLinkedOpenHashMap queuedSources = new Short2ByteLinkedOpenHashMap();
        int neighboursWithSources;

        Section(int sectionX, int sectionZ) {
            this.sectionX = sectionX;
            this.sectionZ = sectionZ;
            this.queuedSources.defaultReturnValue(NO_QUEUED_UPDATE);
        }
    }

    /** Lock-free FIFO of section drains. A claim skips nodes overlapping an in-flight one; a done node nulls its section, which wakes parked drainers. */
    private static final class UpdateQueue {

        private volatile Node head;
        private volatile Node tail;

        UpdateQueue() {
            Node dummy = new Node(null);
            dummy.order = -1L;
            head = dummy;
            tail = dummy;
        }

        void append(Node node) {
            for (Node currentTail = tail, curr = currentTail; ; ) {
                Node next = curr.next;
                if (next == null) {
                    node.order = curr.order + 1L;
                    Node witness = curr.compareExchangeNext(null, node);
                    if (witness == null) {
                        if (tail == currentTail) {
                            tail = node;
                        }
                        return;
                    }
                    curr = witness;
                    continue;
                }

                if (curr == currentTail) {
                    curr = next;
                } else {
                    Node refreshedTail = tail;
                    curr = currentTail == refreshedTail ? next : refreshedTail;
                    currentTail = refreshedTail;
                }
            }
        }

        Node peek() {
            for (Node currentHead = head, curr = currentHead; ; ) {
                Node next = curr.next;
                if (curr.section != null) {
                    if (head == currentHead && curr != currentHead) {
                        head = curr;
                    }
                    return curr;
                }

                if (next == null) {
                    if (head == currentHead && curr != currentHead) {
                        head = curr;
                    }
                    return null;
                }
                curr = next;
            }
        }

        long getLastOrder() {
            for (Node currentTail = tail, curr = currentTail; ; ) {
                Node next = curr.next;
                if (next == null) {
                    if (tail == currentTail && curr != currentTail) {
                        tail = curr;
                    }
                    return curr.order;
                }
                curr = next;
            }
        }

        boolean hasRemainingUpdates(long maxOrder) {
            Node node = peek();
            return node != null && node.order <= maxOrder;
        }

        /** First free node not overlapping an in-flight one; null after parking on the first blocker. */
        Node acquireNextOrWait(long maxOrder) {
            List<Node> blocking = new ArrayList<>();

            nodeSearch:
            for (Node curr = peek(); curr != null && curr.order <= maxOrder; curr = curr.next) {
                if (curr.section == null) {
                    continue;
                }

                if (curr.updating) {
                    blocking.add(curr);
                    continue;
                }

                for (Node blocker : blocking) {
                    if (blocker.intersects(curr)) {
                        continue nodeSearch;
                    }
                }

                if (curr.getAndSetUpdating()) {
                    blocking.add(curr);
                    continue;
                }

                return curr;
            }

            if (!blocking.isEmpty()) {
                await(blocking.get(0));
            }
            return null;
        }

        private static void await(Node node) {
            node.waiters.add(Thread.currentThread());
            while (node.section != null) {
                LockSupport.park();
            }
        }

        void remove(Node node) {
            node.section = null;
            peek();

            Thread waiter;
            while ((waiter = node.waiters.poll()) != null) {
                LockSupport.unpark(waiter);
            }
        }

        static final class Node {
            private static final VarHandle NEXT;
            private static final VarHandle UPDATING;

            static {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    NEXT = lookup.findVarHandle(Node.class, "next", Node.class);
                    UPDATING = lookup.findVarHandle(Node.class, "updating", boolean.class);
                } catch (ReflectiveOperationException exception) {
                    throw new ExceptionInInitializerError(exception);
                }
            }

            private final int sectionX;
            private final int sectionZ;
            private final ConcurrentLinkedQueue<Thread> waiters = new ConcurrentLinkedQueue<>();
            private long order;
            private volatile Section section;
            private volatile Node next;
            private volatile boolean updating;

            Node(Section section) {
                this.section = section;
                this.sectionX = section == null ? 0 : section.sectionX;
                this.sectionZ = section == null ? 0 : section.sectionZ;
            }

            boolean intersects(Node other) {
                return Math.max(Math.abs(sectionX - other.sectionX), Math.abs(sectionZ - other.sectionZ)) <= 2;
            }

            private Node compareExchangeNext(Node expected, Node update) {
                return (Node) NEXT.compareAndExchange(this, expected, update);
            }

            private boolean getAndSetUpdating() {
                return (boolean) UPDATING.getAndSet(this, true);
            }
        }
    }

    /** BFS state of one drain: 5x5 section cache plus the two worklists. An entry packs position, level and a direction bitset. */
    private static final class Wavefront {

        private static final ThreadLocal<Wavefront> CACHE = new ThreadLocal<>();

        static Wavefront acquire() {
            Wavefront cached = CACHE.get();
            CACHE.set(null);
            return cached == null ? new Wavefront() : cached;
        }

        static void release(Wavefront wavefront) {
            if (CACHE.get() == null) {
                CACHE.set(wavefront);
            }
        }

        private static final int SECTION_RADIUS = 2;
        private static final int CACHE_WIDTH = 2 * SECTION_RADIUS + 1;
        // smallest width covering [0, SECTION_SIZE * CACHE_WIDTH) encoded positions
        private static final int COORDINATE_BITS = 9;
        private static final int COORDINATE_SIZE = 1 << COORDINATE_BITS;
        private static final long POSITION_MASK = (1L << (COORDINATE_BITS + COORDINATE_BITS)) - 1;
        private static final int DIRECTION_SHIFT = COORDINATE_BITS + COORDINATE_BITS + LEVEL_BITS;
        // 4x4 bitset, bit = (dx + 1) | ((dz + 1) << 2), the eight neighbours set
        private static final long ALL_DIRECTIONS = 0x757L;
        // increase entry checks its level still matches before propagating, set when a decrease clobbered it
        private static final long FLAG_RECHECK_LEVEL = Long.MIN_VALUE;
        // increase entry writes its level to the position first, set when restoring a clobbered source
        private static final long FLAG_WRITE_LEVEL = Long.MIN_VALUE >>> 1;

        private final Section[] sections = new Section[CACHE_WIDTH * CACHE_WIDTH];
        private int encodeOffsetX;
        private int encodeOffsetZ;
        private int coordinateOffset;
        private int sectionIndexOffset;

        final Long2ByteLinkedOpenHashMap updatedPositions = new Long2ByteLinkedOpenHashMap();
        private long[] increaseQueue = new long[SECTION_SIZE * SECTION_SIZE * 2];
        private int increaseQueueLength;
        private long[] decreaseQueue = new long[SECTION_SIZE * SECTION_SIZE * 2];
        private int decreaseQueueLength;

        void setupEncodeOffset(int centerSectionX, int centerSectionZ) {
            int maxCoordinate = SECTION_RADIUS * SECTION_SIZE - 1;
            encodeOffsetX = maxCoordinate - (centerSectionX << SECTION_SHIFT);
            encodeOffsetZ = maxCoordinate - (centerSectionZ << SECTION_SHIFT);
            coordinateOffset = encodeOffsetX + (encodeOffsetZ << COORDINATE_BITS);
            sectionIndexOffset = (SECTION_RADIUS - centerSectionX) + (SECTION_RADIUS - centerSectionZ) * CACHE_WIDTH;
        }

        void setupCaches(LeafsTicketPropagator propagator, int centerSectionX, int centerSectionZ) {
            for (int dz = -1; dz <= 1; ++dz) {
                for (int dx = -1; dx <= 1; ++dx) {
                    int sectionX = centerSectionX + dx;
                    int sectionZ = centerSectionZ + dz;
                    Section section = propagator.sections.get(positionKey(sectionX, sectionZ));
                    if (section == null) {
                        throw new IllegalStateException("Missing section [" + sectionX + ", " + sectionZ + "] in the update area");
                    }

                    sections[sectionX + CACHE_WIDTH * sectionZ + sectionIndexOffset] = section;
                }
            }
        }

        void destroyCaches() {
            Arrays.fill(sections, null);
        }

        boolean hasQueuedSeeds() {
            return increaseQueueLength != 0 || decreaseQueueLength != 0;
        }

        void queueIncreaseSeed(int posX, int posZ, int level) {
            appendToIncreaseQueue(encode(posX, posZ, level) | (ALL_DIRECTIONS << DIRECTION_SHIFT));
        }

        void queueDecreaseSeed(int posX, int posZ, int level) {
            appendToDecreaseQueue(encode(posX, posZ, level) | (ALL_DIRECTIONS << DIRECTION_SHIFT));
        }

        private long encode(int posX, int posZ, int level) {
            return ((long) (posX + (posZ << COORDINATE_BITS) + coordinateOffset) & POSITION_MASK)
                | ((level & (LEVEL_COUNT - 1L)) << (COORDINATE_BITS + COORDINATE_BITS));
        }

        private void appendToIncreaseQueue(long value) {
            if (increaseQueueLength >= increaseQueue.length) {
                resizeIncreaseQueue();
            }
            increaseQueue[increaseQueueLength++] = value;
        }

        private void appendToDecreaseQueue(long value) {
            if (decreaseQueueLength >= decreaseQueue.length) {
                resizeDecreaseQueue();
            }
            decreaseQueue[decreaseQueueLength++] = value;
        }

        private long[] resizeIncreaseQueue() {
            return increaseQueue = Arrays.copyOf(increaseQueue, increaseQueue.length + (increaseQueue.length >>> 1));
        }

        private long[] resizeDecreaseQueue() {
            return decreaseQueue = Arrays.copyOf(decreaseQueue, decreaseQueue.length + (decreaseQueue.length >>> 1));
        }

        private int getLevel(int posX, int posZ) {
            Section section = sections[(posX >> SECTION_SHIFT) + CACHE_WIDTH * (posZ >> SECTION_SHIFT) + sectionIndexOffset];
            return section == null ? 0 : section.levels[localIndex(posX, posZ)] & 0xFF;
        }

        private void setLevel(int posX, int posZ, int level) {
            Section section = sections[(posX >> SECTION_SHIFT) + CACHE_WIDTH * (posZ >> SECTION_SHIFT) + sectionIndexOffset];
            if (section == null) {
                return;
            }

            int localIndex = localIndex(posX, posZ);
            section.levels[localIndex] = (short) ((section.levels[localIndex] & ~0xFF) | (level & 0xFF));
            updatedPositions.put(positionKey(posX, posZ), (byte) level);
        }

        private void performIncrease() {
            long[] queue = increaseQueue;
            int readIndex = 0;
            int length = increaseQueueLength;
            increaseQueueLength = 0;
            int decodeOffsetX = -encodeOffsetX;
            int decodeOffsetZ = -encodeOffsetZ;

            while (readIndex < length) {
                long queueValue = queue[readIndex++];
                int posX = ((int) queueValue & (COORDINATE_SIZE - 1)) + decodeOffsetX;
                int posZ = (((int) queueValue >>> COORDINATE_BITS) & (COORDINATE_SIZE - 1)) + decodeOffsetZ;
                int propagatedLevel = ((int) queueValue >>> (COORDINATE_BITS + COORDINATE_BITS)) & (LEVEL_COUNT - 1);
                int directions = (int) (queueValue >>> DIRECTION_SHIFT) & 0xFFFF;

                if ((queueValue & FLAG_RECHECK_LEVEL) != 0L) {
                    if (getLevel(posX, posZ) != propagatedLevel) {
                        continue;
                    }
                } else if ((queueValue & FLAG_WRITE_LEVEL) != 0L) {
                    setLevel(posX, posZ, propagatedLevel);
                }

                long uncovered = ~(0x70707L << (1 | (1 << 3)));
                int toPropagate = propagatedLevel - 1;

                for (int i = 0, directionCount = Integer.bitCount(directions); i < directionCount; ++i) {
                    int direction = Integer.numberOfTrailingZeros(directions);
                    directions &= directions - 1;
                    int dx = direction & 3;
                    int dz = (direction >>> 2) & 3;
                    int offX = (posX - 1) + dx;
                    int offZ = (posZ - 1) + dz;

                    int sectionIndex = (offX >> SECTION_SHIFT) + (offZ >> SECTION_SHIFT) * CACHE_WIDTH + sectionIndexOffset;
                    int localIndex = (offX & (SECTION_SIZE - 1)) | ((offZ & (SECTION_SIZE - 1)) << SECTION_SHIFT);
                    int start = dx | (dz << 3);
                    long line1 = uncovered & (7L << start);
                    long line2 = uncovered & (7L << (start + 8));
                    long line3 = uncovered & (7L << (start + 16));
                    uncovered ^= line1 | line2 | line3;
                    Section section = sections[sectionIndex];
                    short stored = section.levels[localIndex];
                    int currentLevel = stored & 0xFF;

                    if (currentLevel >= toPropagate) {
                        continue;
                    }

                    section.levels[localIndex] = (short) ((stored & ~0xFF) | toPropagate);
                    updatedPositions.putAndMoveToLast(positionKey(offX, offZ), (byte) toPropagate);

                    if (toPropagate > 1) {
                        long childDirections = ((line1 >>> start) << DIRECTION_SHIFT)
                            | ((line2 >>> (start + 8)) << (4 + DIRECTION_SHIFT))
                            | ((line3 >>> (start + 16)) << (8 + DIRECTION_SHIFT));

                        if (length >= queue.length) {
                            queue = resizeIncreaseQueue();
                        }

                        queue[length++] = encode(offX, offZ, toPropagate) | childDirections;
                    }
                }
            }
        }

        /** Runs the decrease worklist, then the increase pass that restores clobbered sources. */
        void performDecrease() {
            long[] queue = decreaseQueue;
            int readIndex = 0;
            int length = decreaseQueueLength;
            decreaseQueueLength = 0;
            int decodeOffsetX = -encodeOffsetX;
            int decodeOffsetZ = -encodeOffsetZ;

            while (readIndex < length) {
                long queueValue = queue[readIndex++];
                int posX = ((int) queueValue & (COORDINATE_SIZE - 1)) + decodeOffsetX;
                int posZ = (((int) queueValue >>> COORDINATE_BITS) & (COORDINATE_SIZE - 1)) + decodeOffsetZ;
                int propagatedLevel = ((int) queueValue >>> (COORDINATE_BITS + COORDINATE_BITS)) & (LEVEL_COUNT - 1);
                int directions = (int) (queueValue >>> DIRECTION_SHIFT) & 0xFFFF;
                int toPropagate = propagatedLevel - 1;

                for (int i = 0, directionCount = Integer.bitCount(directions); i < directionCount; ++i) {
                    int direction = Integer.numberOfTrailingZeros(directions);
                    directions &= directions - 1;
                    int dx = direction & 3;
                    int dz = (direction >>> 2) & 3;
                    int offX = (posX - 1) + dx;
                    int offZ = (posZ - 1) + dz;
                    int sectionIndex = (offX >> SECTION_SHIFT) + (offZ >> SECTION_SHIFT) * CACHE_WIDTH + sectionIndexOffset;
                    int localIndex = (offX & (SECTION_SIZE - 1)) | ((offZ & (SECTION_SIZE - 1)) << SECTION_SHIFT);

                    Section section = sections[sectionIndex];
                    short stored = section.levels[localIndex];
                    int currentLevel = stored & 0xFF;
                    int sourceLevel = (stored >>> 8) & 0xFF;

                    if (currentLevel == 0) {
                        continue;
                    }

                    if (currentLevel > toPropagate) {
                        appendToIncreaseQueue(encode(offX, offZ, currentLevel) | (ALL_DIRECTIONS << DIRECTION_SHIFT) | FLAG_RECHECK_LEVEL);
                        continue;
                    }

                    section.levels[localIndex] = (short) (stored & ~0xFF);
                    updatedPositions.putAndMoveToLast(positionKey(offX, offZ), (byte) 0);

                    if (sourceLevel != 0) {
                        appendToIncreaseQueue(encode(offX, offZ, sourceLevel) | (ALL_DIRECTIONS << DIRECTION_SHIFT) | FLAG_WRITE_LEVEL);
                    }

                    if (length >= queue.length) {
                        queue = resizeDecreaseQueue();
                    }
                    queue[length++] = encode(offX, offZ, toPropagate) | (ALL_DIRECTIONS << DIRECTION_SHIFT);
                }
            }

            performIncrease();
        }
    }
}
