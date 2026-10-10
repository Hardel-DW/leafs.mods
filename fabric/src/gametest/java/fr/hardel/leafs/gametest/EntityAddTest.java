package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Work;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class EntityAddTest {

    /** 2026-09-26: a mod thread that did not own the chunk added its entity itself, while the region iterated the entities of that chunk. */
    @GameTest(maxTicks = 100)
    public void theOwnerOfTheChunkAddsTheEntityOfAModThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = EntityTypes.PIG.create(level, EntitySpawnReason.EVENT);
        pig.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ChunkPos chunk = pig.chunkPosition();
        AtomicBoolean addedByTheOwner = new AtomicBoolean();
        ServerEntityEvents.ENTITY_LOAD.register((entity, _) -> {
            if (entity == pig) {
                addedByTheOwner.set(LevelChunks.of(level).owners().holds(chunk.x(), chunk.z()));
            }
        });

        new Thread(() -> level.addFreshEntity(pig), "leafs-gametest-mod").start();
        helper.succeedWhen(() -> helper.assertTrue(addedByTheOwner.get(), "the owner of %s adds the pig of a mod thread".formatted(chunk)));
    }

    /** 2026-10-10: an entity born in a region tick waited for the photo of the next tick, a thrown pearl ran one tick behind its client. */
    @GameTest(maxTicks = 400)
    public void anEntityBornInARegionTickTicksInThatTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtomicLong missedTicks = new AtomicLong(-1);
        FallingSand.whenItFalls(helper, new BlockPos(1, 6, 1), falling -> {
            long birth = level.getGameTime();
            ChunkPos chunk = falling.chunkPosition();
            LevelChunks.of(level).owners().later(chunk.x(), chunk.z(), Work.GAME, () -> missedTicks.set(level.getGameTime() - birth + 1 - falling.tickCount));
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(missedTicks.get() >= 0, "the sand has not fallen yet");
            helper.assertValueEqual(missedTicks.get(), 0L, "region ticks the falling sand missed");
        });
    }
}
