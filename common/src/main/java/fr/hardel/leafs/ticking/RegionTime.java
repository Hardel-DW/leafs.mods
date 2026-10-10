package fr.hardel.leafs.ticking;

import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The game time of a region and its tick state, both advanced by the region tick as vanilla advances the server ones. */
public final class RegionTime {
    private final TickRateManager rate = new TickRateManager();
    private volatile long tick;
    private volatile long stepSeen;
    private volatile int stepLeft;
    private volatile long sprintSeen;
    private volatile long sprintLeft;
    private boolean sprintTick;

    public RegionTime(long tick, TickState state) {
        this.tick = tick;
        this.stepSeen = state.step().id();
        this.sprintSeen = state.sprint().id();
        this.sprintLeft = state.sprint().serverLeft();
        rate.setTickRate(state.tickRate());
        rate.setFrozen(state.frozen());
        rate.setFrozenTicksToRun((int) state.step().serverLeft());
        stepLeft = rate.frozenTicksToRun();
    }

    public long currentTick() {
        return tick;
    }

    public TickRateManager rate() {
        return rate;
    }

    public void inherit(RegionTime parent) {
        tick = parent.tick;
        stepSeen = parent.stepSeen;
        sprintSeen = parent.sprintSeen;
        sprintLeft = parent.sprintLeft;
        rate.setTickRate(parent.rate.tickrate());
        rate.setFrozen(parent.rate.isFrozen());
        rate.setFrozenTicksToRun(parent.rate.frozenTicksToRun());
        stepLeft = parent.stepLeft;
    }

    public void beginTick(TickState state) {
        rate.setTickRate(state.tickRate());
        rate.setFrozen(state.frozen());
        if (state.step().id() != stepSeen) {
            stepSeen = state.step().id();
            rate.setFrozenTicksToRun((int) state.step().ticks());
        }

        if (state.sprint().id() != sprintSeen) {
            sprintSeen = state.sprint().id();
            sprintLeft = state.sprint().ticks();
        }

        sprintTick = sprintLeft > 0;
        if (sprintTick) {
            rate.setFrozen(false);
        }

        rate.tick();
        stepLeft = rate.frozenTicksToRun();
        if (rate.runsNormally()) {
            tick++;
        }
    }

    public void endTick() {
        if (sprintTick) {
            sprintLeft--;
        }
    }

    /** A sprint runs its ticks back to back. */
    public long nextStartDelayNanos() {
        return sprintLeft > 0 ? 0L : rate.nanosecondsPerTick();
    }

    public boolean sprinted(TickState.Order sprint) {
        return sprintSeen == sprint.id() && sprintLeft == 0;
    }

    public boolean stepped(TickState.Order step) {
        return stepSeen == step.id() && stepLeft == 0;
    }

    public void adopt(LevelChunk chunk) {
        retime(chunk.blockTicks, chunk.getLevel(), this);
        retime(chunk.fluidTicks, chunk.getLevel(), this);
    }

    public static long now(LevelChunkTicks<?> ticks, Level level) {
        return timeOf(level, ((ClockedTicks) ticks).leafs$clock());
    }

    /** Writes the ticks in another time, each keeping its delay. */
    public static void retime(LevelChunkTicks<?> ticks, Level level, @Nullable RegionTime clock) {
        long offset = timeOf(level, clock) - now(ticks, level);
        ((ClockedTicks) ticks).leafs$clock(clock);
        if (offset != 0) {
            shift(ticks, offset);
        }
    }

    private static long timeOf(Level level, @Nullable RegionTime clock) {
        return clock == null ? level.getLevelData().getGameTime() : clock.tick;
    }

    private static <T> void shift(LevelChunkTicks<T> ticks, long offset) {
        List<ScheduledTick<T>> shifted = new ArrayList<>();
        for (ScheduledTick<T> tick = ticks.poll(); tick != null; tick = ticks.poll()) {
            shifted.add(new ScheduledTick<>(tick.type(), tick.pos(), tick.triggerTick() + offset, tick.priority(), tick.subTickOrder()));
        }

        shifted.forEach(ticks::schedule);
    }
}
