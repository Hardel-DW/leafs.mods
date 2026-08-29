package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The region's ticking entities this tick, read from the sections of its chunks at tick start. A chunk crossing mid-pass ticks once, in the pass that saw it; a level crossing hands the entity to the other level's region at once. */
public final class RegionEntities {
    private final List<Entity> entities = new ArrayList<>();
    private final IntOpenHashSet ids = new IntOpenHashSet();
    private ServerLevel level;
    private long lastTrackingNanos;

    /** Vanilla's tick list membership: a ticking section, or an entity that always ticks such as a player. */
    public void refresh(ServerLevel level, List<ChunkHolder> holders) {
        this.level = level;
        entities.clear();
        ids.clear();
        EntitySectionStorage<Entity> storage = level.entityManager.sectionStorage;
        for (ChunkHolder holder : holders) {
            storage.getExistingSectionsInChunk(holder.getPos().pack()).forEach(this::collect);
        }
    }

    private void collect(EntitySection<Entity> section) {
        boolean ticking = section.getStatus().isTicking();
        section.getEntities().forEach(entity -> {
            if (!entity.isRemoved() && (ticking || entity.isAlwaysTicking())) {
                entities.add(entity);
                ids.add(entity.getId());
            }
        });
    }

    /** An entity that left for another level since the photo is that level's region's, and skipped here. */
    public void forEach(Consumer<Entity> action) {
        for (Entity entity : entities) {
            if (entity.level() == level) {
                action.accept(entity);
            }
        }
    }

    public void forEachMob(Consumer<Mob> action) {
        forEach(entity -> {
            if (entity instanceof Mob mob) {
                action.accept(mob);
            }
        });
    }

    public boolean contains(Entity entity) {
        return ids.contains(entity.getId());
    }

    public int size() {
        return entities.size();
    }

    /** Start of the last tracking pass; a player who moved since is re-checked against every entity. */
    public long lastTrackingNanos() {
        return lastTrackingNanos;
    }

    public void markTracking(long nowNanos) {
        lastTrackingNanos = nowNanos;
    }
}
