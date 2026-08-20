package fr.hardel.leafs.entity;

import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-08-20: a player teleported to virgin terrain stayed in the attached list forever once a region formed on his chunks, refused FOREIGN on every global listener tick. */
class LevelEntityListsTest {
    private static final long HOME_CHUNK = ChunkPos.pack(-215, 42);

    private final LevelEntityLists lists = new LevelEntityLists();
    private final RegionEntityData region = new RegionEntityData();

    @AfterEach
    void leaveContext() {
        WorldTickContext.exit();
    }

    @Test
    void aStrayJoinsTheRegionThatAppearsOnItsChunk() {
        own(lists.attached());
        lists.attached().tickList().add(7, null, HOME_CHUNK);
        lists.attached().navigatingMobs().add(7, null, HOME_CHUNK);

        lists.route(_ -> region);
        lists.rehomeStrays();

        assertFalse(lists.attached().tickList().contains(7));
        assertFalse(lists.attached().navigatingMobs().contains(7));

        own(region);
        region.tickList().beginTick();
        region.navigatingMobs().beginTick();

        assertTrue(region.tickList().contains(7));
        assertTrue(region.navigatingMobs().contains(7));
    }

    @Test
    void aStrayNoRegionOwnsStaysAttached() {
        own(lists.attached());
        lists.attached().tickList().add(7, null, HOME_CHUNK);

        lists.rehomeStrays();

        assertTrue(lists.attached().tickList().contains(7));
    }

    private void own(RegionEntityData home) {
        WorldTickContext.exit();
        WorldTickContext.enter(new Object(), null, home);
    }
}
