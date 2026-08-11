package fr.hardel.leafs.world;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionWorldDataTest {
    private static final int SECTION_SHIFT = 4;

    private static RegionWorldData worldData(long clockStart) {
        return new RegionWorldData(new RegionClock(clockStart), _ -> true, new ObjectLinkedOpenHashSet<>(), RandomSource.create(), null, new HashSet<>(), new PathTypeCache());
    }

    private static BlockEventData eventIn(int chunkX, int chunkZ) {
        return new BlockEventData(new BlockPos(chunkX << 4, 64, chunkZ << 4), null, 0, 0);
    }

    private static long keyOf(FakeTicker ticker) {
        return ChunkPos.pack(ticker.pos);
    }

    @Test
    void mergeMovesContainersTransfersEventsAndFoldsTime() {
        RegionWorldData from = worldData(100);
        RegionWorldData into = worldData(1000);
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        from.blockTicks().addContainer(new ChunkPos(0, 0), container);
        BlockEventData event = eventIn(0, 0);
        from.blockEvents().add(event);
        from.nextSubTick();
        from.nextSubTick();
        from.nextSubTick();

        from.mergeInto(into);

        assertSame(container, into.blockTicks().containerAt(0, 0));
        assertNull(from.blockTicks().containerAt(0, 0));
        assertTrue(into.blockEvents().contains(event));
        assertTrue(from.blockEvents().isEmpty());
        assertEquals(3, into.nextSubTick(), "the survivor's sub-tick counter continues past both regions' maxima");
    }

    @Test
    void splitRebucketsBySectionInheritsTimeAndDropsOrphanEvents() {
        RegionWorldData parent = worldData(500);
        LevelChunkTicks<Block> west = new LevelChunkTicks<>();
        LevelChunkTicks<Block> east = new LevelChunkTicks<>();
        parent.blockTicks().addContainer(new ChunkPos(0, 0), west);
        parent.blockTicks().addContainer(new ChunkPos(17, 0), east);
        parent.blockEvents().add(eventIn(1, 0));
        parent.blockEvents().add(eventIn(40, 0));
        parent.nextSubTick();

        RegionWorldData westChild = worldData(0);
        RegionWorldData eastChild = worldData(0);
        Map<Long, RegionWorldData> children = Map.of(0L, westChild, 1L, eastChild);
        westChild.inheritTimeFrom(parent);
        eastChild.inheritTimeFrom(parent);
        parent.splitInto(SECTION_SHIFT, children::get);

        assertSame(west, westChild.blockTicks().containerAt(0, 0));
        assertSame(east, eastChild.blockTicks().containerAt(17, 0));
        assertNull(parent.blockTicks().containerAt(0, 0));
        assertEquals(1, westChild.blockEvents().size());
        assertTrue(eastChild.blockEvents().isEmpty(), "the orphan event's section has no child: dropped");
        assertTrue(parent.blockEvents().isEmpty());
        assertEquals(500, westChild.clock().currentTick());
        assertEquals(1, eastChild.nextSubTick());
    }

    @Test
    void migrationRoutesToOwnersAndKeepsStrays() {
        RegionWorldData attached = worldData(0);
        RegionWorldData owner = worldData(0);
        LevelChunkTicks<Block> container = new LevelChunkTicks<>();
        attached.blockTicks().addContainer(new ChunkPos(0, 0), container);
        BlockEventData owned = eventIn(1, 0);
        BlockEventData stray = eventIn(40, 0);
        attached.blockEvents().add(owned);
        attached.blockEvents().add(stray);

        attached.migrateInto(SECTION_SHIFT, section -> section == 0L ? owner : null);

        assertSame(container, owner.blockTicks().containerAt(0, 0));
        assertTrue(owner.blockEvents().contains(owned));
        assertTrue(attached.blockEvents().contains(stray), "an event without an owning region stays attached");
        assertFalse(attached.blockEvents().contains(owned));
    }

    @Test
    void clockAdvanceIsCounterModeOnly() {
        RegionClock counter = new RegionClock(10L);
        counter.advance(3);
        assertEquals(13, counter.currentTick());

        RegionClock attached = new RegionClock(() -> 5L);
        assertThrows(IllegalStateException.class, () -> attached.advance(1));
    }

    @Test
    void inhabitedTimeAdvancesByGlobalDelta() {
        RegionWorldData data = worldData(0);
        assertEquals(0, data.advanceInhabitedTime(0));
        assertEquals(7, data.advanceInhabitedTime(7));
        assertEquals(3, data.advanceInhabitedTime(10));
    }

    @Test
    void blockEventDrainRunsCascadesThisTickAndRequeuesUntickable() {
        RegionWorldData data = worldData(0);
        BlockEventData first = eventIn(0, 0);
        BlockEventData cascade = eventIn(1, 0);
        BlockEventData untickable = eventIn(2, 0);
        data.blockEvents().add(first);
        data.blockEvents().add(untickable);

        List<BlockEventData> executed = new ArrayList<>();
        data.runBlockEvents(pos -> pos.getX() != untickable.pos().getX(), event -> {
            executed.add(event);
            if (event == first) {
                data.blockEvents().add(cascade);
            }
        });

        assertEquals(List.of(first, cascade), executed);
        assertEquals(1, data.blockEvents().size());
        assertTrue(data.blockEvents().contains(untickable), "the untickable event waits for the next tick");
    }

    @Test
    void tickersFollowVanillaSemantics() {
        RegionWorldData data = worldData(0);
        FakeTicker healthy = new FakeTicker(new BlockPos(0, 64, 0));
        FakeTicker removed = new FakeTicker(new BlockPos(16, 64, 0));
        FakeTicker untickable = new FakeTicker(new BlockPos(32, 64, 0));
        removed.removed = true;
        data.blockEntityTickers().add(healthy, keyOf(healthy));
        data.blockEntityTickers().add(removed, keyOf(removed));
        data.blockEntityTickers().add(untickable, keyOf(untickable));
        FakeTicker addedMidTick = new FakeTicker(new BlockPos(48, 64, 0));
        healthy.onTick = () -> data.blockEntityTickers().add(addedMidTick, keyOf(addedMidTick));

        data.blockEntityTickers().tickAll(true, chunkKey -> ChunkPos.getX(chunkKey) != 2);

        assertEquals(1, healthy.ticks);
        assertEquals(0, removed.ticks);
        assertEquals(0, untickable.ticks);
        assertEquals(0, addedMidTick.ticks, "a ticker added mid-pass waits for the next pass");
        assertEquals(3, data.blockEntityTickers().size(), "the removed ticker is gone, the pending one counted");

        healthy.onTick = null;
        data.blockEntityTickers().tickAll(true, _ -> true);
        assertEquals(1, addedMidTick.ticks);
        assertEquals(1, untickable.ticks);
    }

    @Test
    void tickersFoldOnMergeAndSplit() {
        RegionWorldData from = worldData(0);
        RegionWorldData into = worldData(0);
        FakeTicker west = new FakeTicker(new BlockPos(0, 64, 0));
        FakeTicker east = new FakeTicker(new BlockPos(17 << 4, 64, 0));
        FakeTicker orphan = new FakeTicker(new BlockPos(40 << 4, 64, 0));
        from.blockEntityTickers().add(west, keyOf(west));
        from.blockEntityTickers().add(east, keyOf(east));
        from.blockEntityTickers().add(orphan, keyOf(orphan));

        from.mergeInto(into);
        assertEquals(0, from.blockEntityTickers().size());
        assertEquals(3, into.blockEntityTickers().size());

        RegionWorldData westChild = worldData(0);
        RegionWorldData eastChild = worldData(0);
        Map<Long, RegionWorldData> children = Map.of(0L, westChild, 1L, eastChild);
        into.splitInto(SECTION_SHIFT, children::get);

        assertEquals(1, westChild.blockEntityTickers().size());
        assertEquals(1, eastChild.blockEntityTickers().size());
        assertEquals(0, into.blockEntityTickers().size(), "the orphan ticker's section has no child: dropped");
    }

    private static final class FakeTicker implements TickingBlockEntity {
        private final BlockPos pos;
        private boolean removed;
        private int ticks;
        private Runnable onTick;

        private FakeTicker(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public void tick() {
            ticks++;
            if (onTick != null) {
                onTick.run();
            }
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public String getType() {
            return "leafs:fake";
        }
    }
}
