package fr.hardel.leafs.gametest;

import fr.hardel.leafs.ticking.LevelRegions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

public final class MobCensusTest {
    private static final int SPIDERS = 3;

    /** 2026-09-24: a region without a player nearby never published its census, so the level cap kept a stale count. */
    @GameTest(maxTicks = 100)
    public void aRegionWithoutPlayersCountsItsMobs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChunkPos chunk = ChunkPos.containing(helper.absolutePos(pos));
        for (int spider = 0; spider < SPIDERS; spider++) {
            helper.spawnEntity(EntityTypes.SPIDER, pos).requirePersistence(false).spawn();
        }

        helper.succeedWhen(() -> {
            int counted = LevelRegions.of(helper.getLevel()).regionizer().regionAt(chunk.x(), chunk.z()).data().worldData().census().counts()[MobCategory.MONSTER.ordinal()];
            helper.assertTrue(counted >= SPIDERS, "the region counts %s monsters out of %s spiders".formatted(counted, SPIDERS));
        });
    }
}
