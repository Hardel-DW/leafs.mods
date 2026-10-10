package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.chunk.owner.Work;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.atomic.AtomicLong;

/** A region tick runs its steps in the order of a vanilla tick. */
public final class TickOrderTest {

    /** 2026-10-10: a task that reached a waiting region ran at the end of its next tick, so a chunk made ready between two ticks reached its player one tick late. */
    @GameTest(maxTicks = 400)
    public void aTaskThatReachesAWaitingRegionRunsBeforeItsNextTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkOwners owners = LevelChunks.of(level).owners();
        AtomicLong ticksWaited = new AtomicLong(-1);
        FallingSand.whenItFalls(helper, new BlockPos(1, 6, 1), falling -> {
            ChunkPos chunk = falling.chunkPosition();
            owners.later(chunk.x(), chunk.z(), Work.GAME, () -> {
                long postedAt = level.getGameTime();
                owners.later(chunk.x(), chunk.z(), Work.GAME, () -> ticksWaited.set(level.getGameTime() - postedAt));
            });
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(ticksWaited.get() >= 0, "the task has not run yet");
            helper.assertValueEqual(ticksWaited.get(), 0L, "region ticks the task waited");
        });
    }
}
