package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.config.LeafsConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import fr.hardel.leafs.ownership.RegionContext;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
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
        Leafs.LOGGER.info("Leafs ticking attached - every level ticks through its region unit on the server thread; the free-running pool starts at M11");
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

    public Collection<LevelTickUnit> units() {
        return levelUnits.values();
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

    public void shutdown(MinecraftServer server) {
        scheduler.shutdown();
        watchdog.stop();
        drainRegions(server);
    }

    /**
     * One last handshake per level so emptied regions are reclaimed and what is left is reported. It
     * walks {@code getAllLevels()} rather than the units, which only hold levels that ticked at least
     * once, and it never throws: this runs after the worlds are saved, where a crash would only
     * misattribute the shutdown.
     */
    private void drainRegions(MinecraftServer server) {
        int regions = 0;
        int sections = 0;
        for (ServerLevel level : server.getAllLevels()) {
            LevelRegions levelRegions = ((ServerLevelRegionAccess) level).leafs$regions();
            try {
                levelRegions.settle();
            } catch (RuntimeException exception) {
                Leafs.LOGGER.error("Leafs region drain failed on {}", level.dimension().identifier(), exception);
            }

            regions += levelRegions.regionizer().regionsView().size();
            sections += levelRegions.sections();
        }

        if (regions == 0 && sections == 0) {
            Leafs.LOGGER.info("Leafs regions drained: 0 regions, 0 sections");
        } else {
            Leafs.LOGGER.error("Leafs regions NOT drained: {} regions and {} sections outlived the chunk holders that feed them", regions, sections);
        }
    }

    private LevelTickUnit unitFor(ServerLevel level) {
        return levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level));
    }
}
