package fr.hardel.leafs.entity;

import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.world.WorldTickContext;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * A tick unit's entity list. The owning unit reads and writes its ordered maps directly; any other
 * thread (a neighbour whose piston pushes an entity across the boundary, the global phase placing a
 * player) funnels its mutation through a concurrent per-id mailbox that the owner folds in at the
 * start of its tick. Cross-list arrivals always go through the mailbox, owner included, so an
 * entity is never ticked twice in one pass. Merge, split and migration run under the level's write
 * lock and fold the mailbox first, so a queued op never outlives an ownership change.
 */
public final class RegionEntityTickList<E> {
    private final RegionEntityData home;
    private final ConcurrentHashMap<Integer, PendingOp<E>> pending = new ConcurrentHashMap<>();
    private Int2ObjectMap<E> active = new Int2ObjectLinkedOpenHashMap<>();
    private Int2ObjectMap<E> passive = new Int2ObjectLinkedOpenHashMap<>();
    private Int2ObjectMap<E> iterated;
    private final Int2LongMap chunkKeys = new Int2LongOpenHashMap();

    private sealed interface PendingOp<E> {
        record Add<E>(E entity, long chunkKey) implements PendingOp<E> {
        }

        record Remove<E>() implements PendingOp<E> {
        }

        record Move<E>(long chunkKey) implements PendingOp<E> {
        }
    }

    RegionEntityTickList(RegionEntityData home) {
        this.home = home;
    }

    public void add(int id, E entity, long chunkKey) {
        if (owned()) {
            pending.remove(id);
            ensureActiveIsNotIterated();
            active.put(id, entity);
            chunkKeys.put(id, chunkKey);
        } else {
            pending.put(id, new PendingOp.Add<>(entity, chunkKey));
        }
    }

    /** Arrival from another list: buffered to the owner's next pass even when the caller is the owner. */
    public void queueAdd(int id, E entity, long chunkKey) {
        pending.put(id, new PendingOp.Add<>(entity, chunkKey));
    }

    public void remove(int id) {
        if (owned()) {
            pending.remove(id);
            ensureActiveIsNotIterated();
            active.remove(id);
            chunkKeys.remove(id);
        } else {
            pending.put(id, new PendingOp.Remove<>());
        }
    }

    public boolean contains(int id) {
        PendingOp<E> op = pending.get(id);
        if (op instanceof PendingOp.Add) {
            return true;
        }
        if (op instanceof PendingOp.Remove) {
            return false;
        }

        return active.containsKey(id);
    }

    public void move(int id, long chunkKey) {
        if (owned()) {
            PendingOp<E> op = pending.computeIfPresent(id, (key, existing) -> moved(existing, chunkKey));
            if (op == null && active.containsKey(id)) {
                chunkKeys.put(id, chunkKey);
            }
        } else {
            pending.compute(id, (key, existing) -> existing == null ? new PendingOp.Move<>(chunkKey) : moved(existing, chunkKey));
        }
    }

    public void beginTick() {
        drainPending();
    }

    /**
     * Iteration order is the owner's insertion order, as vanilla. A non-owner iterating here only
     * ever reads the attached list, whose owner sits behind the exclusion while regions run, so the
     * plain read is safe and skips the reentrancy guard.
     */
    public void forEach(Consumer<E> output) {
        if (!owned()) {
            for (E entity : active.values()) {
                output.accept(entity);
            }

            return;
        }

        if (iterated != null) {
            throw new UnsupportedOperationException("Only one concurrent iteration supported");
        }

        iterated = active;
        try {
            for (E entity : active.values()) {
                output.accept(entity);
            }
        } finally {
            iterated = null;
        }
    }

    public int size() {
        return active.size();
    }

    public void mergeInto(RegionEntityTickList<E> target) {
        drainPending();
        target.ensureActiveIsNotIterated();
        target.active.putAll(active);
        target.chunkKeys.putAll(chunkKeys);
        active.clear();
        chunkKeys.clear();
    }

    /** Entities whose section died with the split are dropped, like the payload's other position-keyed state. */
    public void splitInto(int sectionShift, LongFunction<RegionEntityTickList<E>> targetBySection) {
        drainPending();
        for (int id : active.keySet().toIntArray()) {
            RegionEntityTickList<E> target = targetBySection.apply(sectionOf(chunkKeys.get(id), sectionShift));
            if (target != null) {
                target.ensureActiveIsNotIterated();
                target.active.put(id, active.get(id));
                target.chunkKeys.put(id, chunkKeys.get(id));
            }
        }

        active.clear();
        chunkKeys.clear();
    }

    /** Owner-only sweep: an entry whose chunk has since gained a tick unit leaves for it through the mailbox, like any other cross-list arrival. */
    public void rehome(LongFunction<RegionEntityTickList<E>> targetByChunk) {
        drainPending();
        for (int id : active.keySet().toIntArray()) {
            long chunkKey = chunkKeys.get(id);
            RegionEntityTickList<E> target = targetByChunk.apply(chunkKey);
            if (target == null || target == this) {
                continue;
            }

            target.queueAdd(id, active.get(id), chunkKey);
            remove(id);
        }
    }

    private void drainPending() {
        if (pending.isEmpty()) {
            return;
        }

        ensureActiveIsNotIterated();
        for (Integer boxed : pending.keySet()) {
            int id = boxed;
            switch (pending.remove(boxed)) {
                case PendingOp.Add<E>(E entity, long chunkKey) -> {
                    active.put(id, entity);
                    chunkKeys.put(id, chunkKey);
                }
                case PendingOp.Remove<E> _ -> {
                    active.remove(id);
                    chunkKeys.remove(id);
                }
                case PendingOp.Move<E>(long chunkKey) -> {
                    if (active.containsKey(id)) {
                        chunkKeys.put(id, chunkKey);
                    }
                }
                case null -> {
                }
            }
        }
    }

    private static <E> PendingOp<E> moved(PendingOp<E> existing, long chunkKey) {
        return switch (existing) {
            case PendingOp.Add<E>(E entity, long _) -> new PendingOp.Add<>(entity, chunkKey);
            case PendingOp.Remove<E> remove -> remove;
            case PendingOp.Move<E> _ -> new PendingOp.Move<>(chunkKey);
        };
    }

    private boolean owned() {
        return WorldTickContext.ownsEntityData(home);
    }

    private static long sectionOf(long chunkKey, int sectionShift) {
        return CoordinateKey.pack(CoordinateKey.x(chunkKey) >> sectionShift, CoordinateKey.z(chunkKey) >> sectionShift);
    }

    private void ensureActiveIsNotIterated() {
        if (iterated == active) {
            passive.clear();
            passive.putAll(active);
            Int2ObjectMap<E> current = active;
            active = passive;
            passive = current;
        }
    }
}
