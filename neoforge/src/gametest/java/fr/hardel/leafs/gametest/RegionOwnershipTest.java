package fr.hardel.leafs.gametest;

import fr.hardel.leafs.region.Regionizer;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionTickData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

public final class RegionOwnershipTest {

    public static void theTestChunkIsOwnedByARegion(GameTestHelper helper) {
        ChunkPos chunk = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        Regionizer<RegionTickData> regionizer = LevelRegions.of(helper.getLevel()).regionizer();
        helper.succeedWhen(() -> helper.assertTrue(regionizer.regionAt(chunk.x(), chunk.z()) != null, "no Leafs region owns the test chunk %s".formatted(chunk)));
    }
}
