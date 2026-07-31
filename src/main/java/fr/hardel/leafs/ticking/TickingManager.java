package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.config.LeafsConfig;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Server-scoped orchestrator of the region tick machinery, reached through {@link LeafsServerAccess}. */
public final class TickingManager {
    private final TickBarrier barrier = new TickBarrier();
    private final LeafsWatchdog watchdog;
    private final RegionTickScheduler scheduler;
    private final Map<ServerLevel, LevelTickUnit> levelUnits = new HashMap<>();
    private final AtomicLong nextUnitId = new AtomicLong(1);

    public TickingManager(LeafsConfig config) {
        this.watchdog = new LeafsWatchdog(Duration.ofSeconds(config.watchdogWarnSeconds()), Leafs.LOGGER::error);
        RegionCrashWriter crashWriter = new RegionCrashWriter(Path.of("crash-reports"));
        this.scheduler = new RegionTickScheduler(config.effectiveRegionThreads(), barrier, watchdog, crashWriter, (handle, throwable) -> Leafs.LOGGER.error("Region tick failed on #{} in {}", handle.id(), handle.dimension(), throwable));
        watchdog.start();
        scheduler.start();
        Leafs.LOGGER.info("Leafs ticking engaged — attached mode, {} region threads standing by", config.effectiveRegionThreads());
    }

    /** Runs one vanilla level tick through the level's region unit: context, crash scope and watchdog engaged. */
    public void tickLevel(ServerLevel level, Runnable vanillaTick) {
        LevelTickUnit unit = levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level));
        unit.prepareAttached(vanillaTick);
        scheduler.runAttached(unit);
    }

    public void shutdown() {
        scheduler.shutdown();
        watchdog.stop();
    }
}
