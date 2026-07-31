package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.config.LeafsConfig;
import net.minecraft.server.level.ServerLevel;

import fr.hardel.leafs.ownership.RegionContext;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Server-scoped orchestrator of the region tick machinery, reached through {@link LeafsServerAccess}. */
public final class TickingManager {
    private static final List<LevelTickPhases> installedPhases = new CopyOnWriteArrayList<>();

    private final TickBarrier barrier = new TickBarrier();
    private final LeafsWatchdog watchdog;
    private final RegionTickScheduler scheduler;
    private final Map<ServerLevel, LevelTickUnit> levelUnits = new ConcurrentHashMap<>();
    private final AtomicLong nextUnitId = new AtomicLong(1);

    public TickingManager(LeafsConfig config) {
        this.watchdog = new LeafsWatchdog(Duration.ofSeconds(config.watchdogWarnSeconds()), Leafs.LOGGER::error);
        RegionCrashWriter crashWriter = new RegionCrashWriter(Path.of("crash-reports"));
        this.scheduler = new RegionTickScheduler(config.effectiveRegionThreads(), barrier, watchdog, crashWriter, (handle, throwable) -> Leafs.LOGGER.error("Region tick failed on #{} in {}", handle.id(), handle.dimension(), throwable));
        watchdog.start();
        scheduler.start();
        Leafs.LOGGER.info("Leafs ticking engaged — attached mode, {} region threads standing by", config.effectiveRegionThreads());
    }

    /** Installed at bootstrap, before any server exists; before-hooks run in install order. */
    public static void installPhases(LevelTickPhases phases) {
        installedPhases.add(Objects.requireNonNull(phases));
    }

    static List<LevelTickPhases> phases() {
        return installedPhases;
    }

    public TickBarrier barrier() {
        return barrier;
    }

    /** Runs one vanilla level tick through the level's region unit: context, crash scope and watchdog engaged. */
    public void tickLevel(ServerLevel level, Runnable vanillaTick) {
        LevelTickUnit unit = unitFor(level);
        unit.prepareAttached(vanillaTick);
        scheduler.runAttached(unit);
    }

    /** Runs on the owner's next tick, before its level tick. */
    public void submitToLevel(ServerLevel level, Runnable task) {
        unitFor(level).submit(task);
    }

    public boolean currentThreadOwns(ServerLevel level) {
        return RegionContext.current() instanceof RegionContext.Region context && context.regionId() == unitFor(level).id();
    }

    public void shutdown() {
        scheduler.shutdown();
        watchdog.stop();
    }

    private LevelTickUnit unitFor(ServerLevel level) {
        return levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level));
    }
}
