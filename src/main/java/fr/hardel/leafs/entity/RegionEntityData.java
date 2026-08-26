package fr.hardel.leafs.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.function.LongFunction;

/** Per-region entity tick list and navigating-mob list, folded on merge/split. */
public final class RegionEntityData {
    private final RegionEntityTickList<Entity> tickList = new RegionEntityTickList<>(this);
    private final RegionEntityTickList<Mob> navigatingMobs = new RegionEntityTickList<>(this);
    private long lastTrackingNanos;

    public RegionEntityTickList<Entity> tickList() {
        return tickList;
    }

    public RegionEntityTickList<Mob> navigatingMobs() {
        return navigatingMobs;
    }

    /** Start of the last tracking pass; a player who moved since is re-checked against every entity. */
    public long lastTrackingNanos() {
        return lastTrackingNanos;
    }

    public void markTracking(long nowNanos) {
        lastTrackingNanos = nowNanos;
    }

    /** The older stamp wins: the target re-checks what the source may have missed. */
    public void mergeInto(RegionEntityData target) {
        tickList.mergeInto(target.tickList);
        navigatingMobs.mergeInto(target.navigatingMobs);
        target.lastTrackingNanos = Math.min(target.lastTrackingNanos, lastTrackingNanos);
    }

    public void splitInto(int sectionShift, LongFunction<RegionEntityData> targetBySection) {
        tickList.splitInto(sectionShift, section -> {
            RegionEntityData target = targetBySection.apply(section);

            return target == null ? null : target.tickList;
        });
        navigatingMobs.splitInto(sectionShift, section -> {
            RegionEntityData target = targetBySection.apply(section);

            return target == null ? null : target.navigatingMobs;
        });
    }
}
