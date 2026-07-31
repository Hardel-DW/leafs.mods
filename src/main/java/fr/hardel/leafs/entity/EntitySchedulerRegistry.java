package fr.hardel.leafs.entity;

import fr.hardel.leafs.scheduler.EntityScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server-scoped entity schedulers, keyed by UUID so they survive the entity recreation of
 * cross-dimension teleports. Each scheduler ticks with whichever region currently owns the entity;
 * an unloaded entity's tasks simply wait until it loads again.
 */
public final class EntitySchedulerRegistry {
    private final Map<UUID, EntityScheduler<Entity>> schedulers = new ConcurrentHashMap<>();

    public boolean schedule(Entity entity, long delayTicks, Consumer<Entity> task, Runnable retiredCallback) {
        return schedulers.computeIfAbsent(entity.getUUID(), _ -> new EntityScheduler<>()).schedule(delayTicks, task, retiredCallback);
    }

    /** Permanent removal only (KILLED/DISCARDED) — unload and dimension change keep the scheduler. */
    public void retire(UUID entityId) {
        EntityScheduler<Entity> scheduler = schedulers.remove(entityId);
        if (scheduler != null) {
            scheduler.retire();
        }
    }

    /** Called by the owning unit before its level tick: ticks the schedulers of entities this level holds. */
    public void tickLevel(ServerLevel level) {
        for (Map.Entry<UUID, EntityScheduler<Entity>> entry : schedulers.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (entity != null) {
                entry.getValue().tick(entity);
            }
        }
    }

    public int size() {
        return schedulers.size();
    }
}
