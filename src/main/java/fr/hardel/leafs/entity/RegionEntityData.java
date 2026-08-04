package fr.hardel.leafs.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.function.LongFunction;

/**
 * One tick unit's entity-side payload: the entity tick list and the navigating-mob list, both
 * per-region homes of level-wide vanilla state. Folded on merge/split like the world payload.
 */
public final class RegionEntityData {
    private final RegionEntityTickList<Entity> tickList = new RegionEntityTickList<>();
    private final RegionEntityTickList<Mob> navigatingMobs = new RegionEntityTickList<>();

    public RegionEntityTickList<Entity> tickList() {
        return tickList;
    }

    public RegionEntityTickList<Mob> navigatingMobs() {
        return navigatingMobs;
    }

    public void mergeInto(RegionEntityData target) {
        tickList.mergeInto(target.tickList);
        navigatingMobs.mergeInto(target.navigatingMobs);
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
