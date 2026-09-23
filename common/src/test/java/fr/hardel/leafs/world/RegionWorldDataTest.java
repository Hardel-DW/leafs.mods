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
        return new RegionWorldData(clock::get, RandomSource.create(), null, new PathTypeCache());
    }

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
