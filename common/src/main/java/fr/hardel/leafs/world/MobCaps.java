package fr.hardel.leafs.world;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MobCaps {
    private static final MobCategory[] SPAWNING = Arrays.stream(MobCategory.values()).filter(category -> category != MobCategory.MISC).toArray(MobCategory[]::new);
    private final LeafsConfig.Gameplay gameplay;
    private final LevelRegions regions;

    public MobCaps(ServerLevel level) {
        this.gameplay = LeafsConfig.get().gameplay();
        this.regions = LevelRegions.of(level);
    }

    public List<MobCategory> spawnable(RegionWorldData worldData, NaturalSpawner.SpawnState state, boolean spawnEnemies, boolean spawnPersistent) {
        worldData.publishCensus(MobCensus.of(state));
        MobCensus census = gameplay.mobCapScope() == LeafsConfig.MobCapScope.LEVEL ? levelCensus() : worldData.census();
        List<MobCategory> categories = new ArrayList<>(SPAWNING.length);
        for (MobCategory category : SPAWNING) {
            if ((spawnEnemies || category.isFriendly()) && (spawnPersistent || !category.isPersistent()) && census.below(category, gameplay.mobCap(category))) {
                categories.add(category);
            }
        }

        return categories;
    }

    private MobCensus levelCensus() {
        MobCensus total = MobCensus.EMPTY;
        for (Region<RegionTickData> region : regions.regionizer().regionsView()) {
            total = total.plus(region.data().worldData().census());
        }

        return total;
    }
}
