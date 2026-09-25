package fr.hardel.leafs.gametest;

import fr.hardel.leafs.ticking.LevelRegions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class MobCensusTest {
    private static final int SPIDERS = 3;

    /** 2026-09-24: a region without a player nearby never published its census, so the level cap kept a stale count. */
    @GameTest(maxTicks = 100)
    public void aRegionWithoutPlayersCountsItsMobs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChunkPos chunk = ChunkPos.containing(helper.absolutePos(pos));
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
        for (int spider = 0; spider < SPIDERS; spider++) {
            Mob mob = EntityType.SPIDER.create(level, EntitySpawnReason.STRUCTURE);
            mob.snapTo(at);
            level.addFreshEntity(mob);
        }

        helper.succeedWhen(() -> {
            int counted = LevelRegions.of(helper.getLevel()).regionizer().regionAt(chunk.x(), chunk.z()).data().worldData().census().counts()[MobCategory.MONSTER.ordinal()];
            helper.assertTrue(counted >= SPIDERS, "the region counts %s monsters out of %s spiders".formatted(counted, SPIDERS));
        });
    }
}
