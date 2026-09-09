package fr.hardel.leafs.world;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class RegionWorldDataTest {

    private static RegionWorldData worldData(AtomicLong clock) {
        return new RegionWorldData(clock::get, RandomSource.create(), null, new PathTypeCache(), 0L);
    }

    @Test
    void inhabitedTimeAdvancesByGlobalDelta() {
        RegionWorldData data = worldData(new AtomicLong());
        assertEquals(0, data.advanceInhabitedTime(0));
        assertEquals(7, data.advanceInhabitedTime(7));
        assertEquals(3, data.advanceInhabitedTime(10));
    }

    /** A delay lands on the region clock, never on game time, and the sub-tick counter orders same-tick schedules. */
    @Test
    void aTickIsCreatedOnTheRegionClockInSchedulingOrder() {
        RegionWorldData data = worldData(new AtomicLong(40));
        BlockPos pos = new BlockPos(3, 64, 3);

        ScheduledTick<?> first = data.createTick(pos, Blocks.STONE, 3);
        ScheduledTick<?> second = data.createTick(pos.above(), Blocks.STONE, 3, TickPriority.HIGH);

        assertEquals(43, first.triggerTick());
        assertEquals(43, second.triggerTick());
        assertEquals(TickPriority.HIGH, second.priority());
        assertTrue(first.subTickOrder() < second.subTickOrder());
    }
}
