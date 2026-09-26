package fr.hardel.leafs.gametest;

import fr.hardel.leafs.chunk.LevelChunks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;

public final class EntityAddTest {

    /** 2026-09-26: a mod thread that did not own the chunk added its entity itself, while the region iterated the entities of that chunk. */
    @GameTest(maxTicks = 100)
    public void theOwnerOfTheChunkAddsTheEntityOfAModThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = EntityType.PIG.create(level, EntitySpawnReason.EVENT);
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
}
