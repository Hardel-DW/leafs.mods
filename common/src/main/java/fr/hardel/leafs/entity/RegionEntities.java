package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The region's entities this tick, from the sections of its chunks: the ticking ones, and every accessible one for the spawn census. */
public final class RegionEntities {
    private final List<Entity> entities = new ArrayList<>();
    private final List<Entity> accessible = new ArrayList<>();
    private final IntOpenHashSet ids = new IntOpenHashSet();
    private ServerLevel level;
    private long lastTrackingNanos;

    /** One walk of the sections: vanilla's tick list membership is a ticking section or an entity that always ticks, vanilla's census is every accessible section. */
    public void refresh(ServerLevel level, List<ChunkHolder> holders) {
        this.level = level;
        entities.clear();
        accessible.clear();
        ids.clear();
        EntitySectionStorage<Entity> storage = level.entityManager.sectionStorage;
        for (ChunkHolder holder : holders) {
            ChunkPos pos = holder.getPos();
            for (long key : storage.getChunkSections(pos.x(), pos.z())) {
                EntitySection<Entity> section = storage.sections.get(key);
                if (section != null) {
                    collect(section);
                }
            }
        }
    }

    private void collect(EntitySection<Entity> section) {
        Visibility status = section.getStatus();
        boolean ticking = status.isTicking();
        boolean visible = status.isAccessible();
        section.getEntities().forEach(entity -> {
            if (visible) {
                accessible.add(entity);
            }

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

    /** Vanilla's {@code getAllEntities()} restricted to the region: the spawn census. */
    public List<Entity> accessible() {
        return accessible;
    }

    public boolean contains(Entity entity) {
        return ids.contains(entity.getId());
    }

    public int size() {
        return entities.size();
    }

    /** Start of the last tracking pass; a player who moved since is re-checked against the entities he may see. */
    public long lastTrackingNanos() {
        return lastTrackingNanos;
    }

    public void markTracking(long nowNanos) {
        lastTrackingNanos = nowNanos;
    }
}
