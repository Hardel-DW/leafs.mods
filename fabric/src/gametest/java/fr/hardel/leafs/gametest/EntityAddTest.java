package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class EntityAddTest {
    private static final BlockPos SAND = new BlockPos(1, 6, 1);
    private static final long NOT_LANDED = Long.MIN_VALUE;

    /** 2026-09-26: a mod thread that did not own the chunk added its entity itself, while the region iterated the entities of that chunk. */
    @GameTest(maxTicks = HangGuard.TICKS)
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
    @GameTest(maxTicks = HangGuard.TICKS)
    public void anEntityBornInARegionTickTicksInThatTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(SAND);
        AtomicLong birth = new AtomicLong();
        AtomicLong missedTicks = new AtomicLong(NOT_LANDED);
        ServerEntityEvents.ENTITY_LOAD.register((entity, _) -> {
            if (entity instanceof FallingBlockEntity falling && falling.getStartPos().equals(start)) {
                birth.set(level.getGameTime());
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, _) -> {
            if (entity instanceof FallingBlockEntity falling && falling.getStartPos().equals(start)) {
                missedTicks.set(level.getGameTime() - birth.get() + 1 - falling.tickCount);
            }
        });

        helper.setBlock(SAND, Blocks.SAND);
        helper.succeedWhen(() -> {
            helper.assertTrue(missedTicks.get() != NOT_LANDED, "the sand has not landed yet");
            helper.assertValueEqual(missedTicks.get(), 0L, "region ticks the falling sand missed");
        });
    }
}
