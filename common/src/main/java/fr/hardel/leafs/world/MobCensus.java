package fr.hardel.leafs.world;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

public record MobCensus(int spawnableChunks, int[] counts) {
    private static final int VANILLA_SPAWNABLE_AREA = 17 * 17;
    public static final MobCensus EMPTY = new MobCensus(0, new int[MobCategory.values().length]);

    public static MobCensus of(NaturalSpawner.SpawnState state) {
        int[] counts = new int[MobCategory.values().length];
        for (Object2IntMap.Entry<MobCategory> entry : state.getMobCategoryCounts().object2IntEntrySet()) {
            counts[entry.getKey().ordinal()] = entry.getIntValue();
        }

        return new MobCensus(state.getSpawnableChunkCount(), counts);
    }

    public boolean below(MobCategory category, int capPerArea) {
        return counts[category.ordinal()] < capPerArea * spawnableChunks / VANILLA_SPAWNABLE_AREA;
    }
}
