package fr.hardel.leafs.entity;

import fr.hardel.leafs.ticking.LevelRegions;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public final class RegionEntities {
    private final List<Entity> entities = new ArrayList<>();
    private final List<Entity> accessible = new ArrayList<>();
    private final IntOpenHashSet ids = new IntOpenHashSet();
    private ServerLevel level;
    private long lastTrackingNanos;

    public void refresh(ServerLevel level, Collection<EntitySection<Entity>> sections) {
        this.level = level;
        entities.clear();
        accessible.clear();
        ids.clear();
        sections.forEach(this::collect);

        LevelRegions regions = LevelRegions.of(level);
        for (ServerPlayer player : level.players()) {
            ChunkPos pos = player.chunkPosition();
            if (!player.isRemoved() && regions.tickerAt(pos.x(), pos.z()) == Thread.currentThread() && ids.add(player.getId())) {
                entities.add(player);
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

    public List<Entity> accessible() {
        return accessible;
    }

    public boolean contains(Entity entity) {
        return ids.contains(entity.getId());
    }

    public int size() {
        return entities.size();
    }

    public long lastTrackingNanos() {
        return lastTrackingNanos;
    }

    public void markTracking(long nowNanos) {
        lastTrackingNanos = nowNanos;
    }
}
