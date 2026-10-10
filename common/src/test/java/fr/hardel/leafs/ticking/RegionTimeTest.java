package fr.hardel.leafs.ticking;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.ticks.ScheduledTick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class RegionTimeTest {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final TickState.Order STEP = new TickState.Order(1L, 3L, 0L);
    private static final TickState.Order SPRINT = new TickState.Order(1L, 3L, 0L);

    private static TickState state(boolean frozen, TickState.Order step, TickState.Order sprint) {
        return new TickState(20.0F, NANOS_PER_TICK, frozen, false, step, sprint);
    }

    private static void tick(RegionTime time, TickState state, int ticks) {
        for (int index = 0; index < ticks; index++) {
            time.beginTick(state);
            time.endTick();
        }
    }

    @Test
    void aFrozenRegionKeepsItsTime() {
        RegionTime time = new RegionTime(100L, state(true, TickState.Order.NONE, TickState.Order.NONE));

        tick(time, state(true, TickState.Order.NONE, TickState.Order.NONE), 5);

        assertEquals(100L, time.currentTick());
    }

    @Test
    void aStepRunsExactlyItsTicksThenTheRegionStaysFrozen() {
        RegionTime time = new RegionTime(100L, state(true, TickState.Order.NONE, TickState.Order.NONE));

        tick(time, state(true, STEP, TickState.Order.NONE), 5);

        assertEquals(103L, time.currentTick());
        assertTrue(time.stepped(STEP));
    }

    @Test
    void aStopOrderEndsTheStepOfARegion() {
        RegionTime time = new RegionTime(100L, state(true, TickState.Order.NONE, TickState.Order.NONE));
        TickState.Order longStep = new TickState.Order(1L, 10L, 0L);
        tick(time, state(true, longStep, TickState.Order.NONE), 2);

        TickState.Order stop = new TickState.Order(2L, 0L, 0L);
        tick(time, state(true, stop, TickState.Order.NONE), 5);

        assertEquals(102L, time.currentTick());
        assertTrue(time.stepped(stop));
    }

    @Test
    void aRegionBornDuringAStepRunsWhatTheServerHasLeftOfIt() {
        TickState stepping = state(true, new TickState.Order(1L, 10L, 4L), TickState.Order.NONE);
        RegionTime time = new RegionTime(100L, stepping);

        tick(time, stepping, 6);

        assertEquals(104L, time.currentTick());
    }

    @Test
    void aSprintRunsItsTicksBackToBackThenWaitsItsPeriodAgain() {
        RegionTime time = new RegionTime(100L, state(true, TickState.Order.NONE, TickState.Order.NONE));
        TickState sprinting = state(true, TickState.Order.NONE, SPRINT);

        time.beginTick(sprinting);
        time.endTick();
        assertEquals(0L, time.nextStartDelayNanos());
        assertFalse(time.sprinted(SPRINT));

        tick(time, sprinting, 2);
        assertEquals(103L, time.currentTick(), "a frozen region runs the ticks of a sprint");
        assertTrue(time.sprinted(SPRINT));
        assertEquals(NANOS_PER_TICK, time.nextStartDelayNanos());

        tick(time, sprinting, 2);
        assertEquals(103L, time.currentTick(), "the region is frozen again once its sprint ran");
    }

    @Test
    void aSplitChildStartsOnTheTimeAndTheStepOfItsParent() {
        RegionTime parent = new RegionTime(100L, state(true, TickState.Order.NONE, TickState.Order.NONE));
        TickState stepping = state(true, STEP, TickState.Order.NONE);
        tick(parent, stepping, 1);
        RegionTime child = new RegionTime(0L, TickState.INITIAL);

        child.inherit(parent);
        tick(child, stepping, 5);

        assertEquals(103L, child.currentTick());
    }

    @Test
    void ticksTakenIntoAnotherTimeKeepEachDelay() {
        BlockPos pos = new BlockPos(3, 64, 3);
        RegionTime from = new RegionTime(100L, TickState.INITIAL);
        RegionTime into = new RegionTime(1000L, TickState.INITIAL);
        ClockedChunkTicks<Block> ticks = new ClockedChunkTicks<>(from);
        ticks.schedule(new ScheduledTick<>(Blocks.STONE, pos, 103L, 0L));
        ticks.schedule(new ScheduledTick<>(Blocks.STONE, pos.above(), 110L, 1L));

        RegionTime.retime(ticks, null, into);

        assertEquals(1003L, ticks.peek().triggerTick());
        assertEquals(2, ticks.count());
        assertTrue(ticks.hasScheduledTick(pos.above(), Blocks.STONE));
        assertSame(into, ticks.leafs$clock());
        assertEquals(1000L, RegionTime.now(ticks, null));
    }
}
