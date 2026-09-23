package fr.hardel.leafs.gametest;

import fr.hardel.leafs.ticking.LevelRegions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

record RegionTicks(GameTestHelper helper, BlockPos pos, long target) implements Runnable {

    static RegionTicks after(GameTestHelper helper, BlockPos pos, long ticks) {
        return new RegionTicks(helper, pos, now(helper, pos) + ticks);
    }

    @Override
    public void run() {
        long now = now(helper, pos);
        if (now < target) {
            throw helper.assertionException(pos, Component.literal("Waiting for region tick %s, the region is at %s".formatted(target, now)));
        }
    }

    private static long now(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = ChunkPos.containing(helper.absolutePos(pos));
        return LevelRegions.of(level).timeAt(chunk.x(), chunk.z(), level.getGameTime());
    }
}
