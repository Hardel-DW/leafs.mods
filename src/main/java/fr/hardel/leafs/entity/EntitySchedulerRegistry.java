package fr.hardel.leafs.entity;

import fr.hardel.leafs.scheduler.EntityScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server-scoped entity schedulers, keyed by UUID so they survive the entity recreation of
 * cross-dimension teleports and player respawns. A scheduler ticks once per server tick, with the
 * level that currently holds its entity; while the entity is gone for good the scheduler is retired,
 * so every scheduled task ends in exactly one of its two callbacks.
 */
public final class EntitySchedulerRegistry {
    private final Map<UUID, EntityScheduler<Entity>> schedulers = new ConcurrentHashMap<>();

    /** False when the entity is already gone for good — neither callback could ever fire. */
    public boolean schedule(Entity entity, long delayTicks, Consumer<Entity> task, Runnable retiredCallback) {
        if (isGoneForGood(entity, entity.getRemovalReason())) {
            return false;
        }

        return schedulers.computeIfAbsent(entity.getUUID(), _ -> new EntityScheduler<>()).schedule(delayTicks, task, retiredCallback);
    }

    public void onEntityRemoved(Entity entity, Entity.RemovalReason reason) {
        if (!isGoneForGood(entity, reason)) {
            return;
        }

        EntityScheduler<Entity> scheduler = schedulers.remove(entity.getUUID());
        if (scheduler != null) {
            scheduler.retire();
        }
    }

    /** Called by the owning unit before its level tick: ticks the schedulers of entities this level holds. */
    public void tickLevel(ServerLevel level) {
        for (Map.Entry<UUID, EntityScheduler<Entity>> entry : schedulers.entrySet()) {
            EntityScheduler<Entity> scheduler = entry.getValue();
            if (!scheduler.hasPendingTasks()) {
                continue;
            }

            Entity entity = level.getEntity(entry.getKey());
            if (entity != null) {
                scheduler.tick(entity);
            }
        }
    }

    /**
     * A removal is permanent unless the same UUID comes back: a chunk unload or a logout may never be
     * followed by a reload, so those retire (upstream Paper's rule). Players are the exception — death
     * and dimension change recreate them within the tick — and only their disconnect is permanent.
     */
    private static boolean isGoneForGood(Entity entity, Entity.RemovalReason reason) {
        if (reason == null) {
            return false;
        }

        if (entity instanceof ServerPlayer) {
            return reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER;
        }

        return reason != Entity.RemovalReason.CHANGED_DIMENSION;
    }
}
