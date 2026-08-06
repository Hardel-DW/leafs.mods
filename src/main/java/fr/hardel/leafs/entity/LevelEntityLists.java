package fr.hardel.leafs.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;
import java.util.function.LongFunction;

/** Unsynchronized: ownership already orders all mutators (region mid-tick or level-serial). */
public final class LevelEntityLists {
    private final RegionEntityData attached = new RegionEntityData();
    private volatile LongFunction<RegionEntityData> resolver = chunkKey -> attached;

    public RegionEntityData attached() {
        return attached;
    }

    public void route(LongFunction<RegionEntityData> resolver) {
        this.resolver = resolver;
    }

    /** Ticking entities always sit in loaded chunks, so a missing target here is a broken feed, not a droppable stray. */
    public void migrateAttached(int sectionShift, LongFunction<RegionEntityData> bySection) {
        attached.splitInto(sectionShift, section -> {
            RegionEntityData target = bySection.apply(section);
            if (target == null) {
                throw new IllegalStateException("No region owns section " + section + " while migrating attached entity lists");
            }

            return target;
        });
    }

    public void tickingStarted(Entity entity) {
        long chunkKey = entity.chunkPosition().pack();
        resolver.apply(chunkKey).tickList().add(entity.getId(), entity, chunkKey);
    }

    public void tickingEnded(Entity entity) {
        resolver.apply(entity.chunkPosition().pack()).tickList().remove(entity.getId());
    }

    public boolean containsTicking(Entity entity) {
        return resolver.apply(entity.chunkPosition().pack()).tickList().contains(entity.getId());
    }

    public void navigationStarted(Mob mob) {
        long chunkKey = mob.chunkPosition().pack();
        resolver.apply(chunkKey).navigatingMobs().add(mob.getId(), mob, chunkKey);
    }

    public void navigationEnded(Mob mob) {
        resolver.apply(mob.chunkPosition().pack()).navigatingMobs().remove(mob.getId());
    }

    /** Cross-unit moves buffer at the target so an entity is never ticked twice in one pass. */
    public void sectionMoved(Entity entity, int oldChunkX, int oldChunkZ) {
        long oldChunkKey = ChunkPos.pack(oldChunkX, oldChunkZ);
        long newChunkKey = entity.chunkPosition().pack();
        if (oldChunkKey == newChunkKey) {
            return;
        }

        RegionEntityData from = resolver.apply(oldChunkKey);
        RegionEntityData to = resolver.apply(newChunkKey);
        int id = entity.getId();
        moveEntry(from.tickList(), to.tickList(), id, entity, newChunkKey);
        if (entity instanceof Mob mob && from.navigatingMobs().contains(id)) {
            moveEntry(from.navigatingMobs(), to.navigatingMobs(), id, mob, newChunkKey);
        }
    }

    /** The owning unit's navigating mobs plus attached strays. */
    public void forEachNavigatingMobAt(long chunkKey, Consumer<Mob> action) {
        RegionEntityData owner = resolver.apply(chunkKey);
        owner.navigatingMobs().forEach(action);
        if (owner != attached) {
            attached.navigatingMobs().forEach(action);
        }
    }

    private static <E> void moveEntry(RegionEntityTickList<E> from, RegionEntityTickList<E> to, int id, E entity, long newChunkKey) {
        if (!from.contains(id)) {
            return;
        }

        if (from == to) {
            from.move(id, newChunkKey);
        } else {
            from.remove(id);
            to.queueAdd(id, entity, newChunkKey);
        }
    }
}
