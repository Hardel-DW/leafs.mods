package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.entity.EntityTeleports;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.scheduler.GlobalScheduler;
import fr.hardel.leafs.scheduler.RegionScheduler;
import fr.hardel.leafs.scheduler.SharedChunkHolds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Server-scoped orchestrator of the region tick machinery, reached through {@link LeafsServerAccess}. */
public final class TickingManager {

    private final MinecraftServer server;
    private final TickBarrier barrier = new TickBarrier();
    private final LeafsWatchdog watchdog;
    private final RegionTickScheduler scheduler;
    private final GlobalScheduler globalScheduler = new GlobalScheduler();
    private final Map<ServerLevel, LevelTickUnit> levelUnits = new ConcurrentHashMap<>();
    private final AtomicLong nextUnitId = new AtomicLong(1);
    private volatile boolean globalTicking;

    public TickingManager(MinecraftServer server, LeafsConfig config) {
        this.server = server;
        this.watchdog = new LeafsWatchdog(Duration.ofSeconds(config.watchdogWarnSeconds()), Leafs.LOGGER::error);
        RegionCrashWriter crashWriter = new RegionCrashWriter(Path.of("crash-reports"));
        this.scheduler = new RegionTickScheduler(config.effectiveRegionThreads(), config.perRegionLogs(), barrier, watchdog, crashWriter, this::onRegionTickFailure);
        watchdog.start();
        scheduler.start();
        Leafs.LOGGER.info("Leafs ticking live - {} region workers; regions tick free-running, the serial remainder stays on the server thread", config.effectiveRegionThreads());
    }


    public TickBarrier barrier() {
        return barrier;
    }

    /** Rare global-phase work reaching entities regions own (player teardown): runs with every region paused. */
    public void runWithRegionsPaused(Runnable action) {
        barrier.raise();
        try {
            action.run();
        } finally {
            barrier.drop();
        }
    }

    public GlobalScheduler globalScheduler() {
        return globalScheduler;
    }

    /**
     * Compromise #6: once regions may be live, an off-thread {@code MinecraftServer.execute} lands in
     * the global phase. A designed hot path since the listener-tick move (chunk acks, teardown, handler continuations), so it logs nothing.
     */
    public boolean divertExecute(Runnable task) {
        if (!globalTicking || server.isSameThread() || server.isStopped()) {
            return false;
        }

        globalScheduler.run(task);
        return true;
    }

    public Collection<LevelTickUnit> units() {
        return levelUnits.values();
    }

    public void tickLevel(ServerLevel level, Runnable vanillaTick) {
        globalTicking = true;
        LevelTickUnit unit = unitFor(level);
        unit.ensureActivated();
        unit.prepareAttached(vanillaTick);
        scheduler.runAttached(unit);
    }

    /** Runs on the owner's next tick, before its level tick. */
    public void submitToLevel(ServerLevel level, Runnable task) {
        unitFor(level).submit(task);
    }

    public void tickPausedNetwork() {
        for (LevelTickUnit unit : levelUnits.values()) {
            unit.tickPausedNetwork();
        }
    }

    public boolean currentThreadOwns(ServerLevel level) {
        return RegionContext.current() instanceof RegionContext.LevelSerial context && context.id() == unitFor(level).id();
    }

    /** Ticket ops first, then bookkeeping (which runs the distance updates that materialise holders), then offers. */
    public void quiesce() {
        for (ServerLevel level : server.getAllLevels()) {
            LevelRegions regions = ((ServerLevelRegionAccess) level).leafs$regions();
            LevelOwnership ownership = regions.ownership();
            ownership.enterLevelSerial();
            try {
                SharedChunkHolds holds = regions.holds();
                if (holds != null) {
                    holds.applyPendingOps();
                }

                drainChunkBookkeeping(level);
                RegionScheduler<RegionTickData> taskScheduler = regions.taskScheduler();
                if (taskScheduler != null) {
                    taskScheduler.completePending();
                }

                regions.rethrowFeedFailure();
            } finally {
                ownership.exitLevelSerial();
            }
        }
    }

    private void drainChunkBookkeeping(ServerLevel level) {
        boolean hasMore = true;
        while (hasMore) {
            hasMore = level.getChunkSource().pollTask();
        }
    }

    public void setTickPeriodNanos(long periodNanos) {
        scheduler.setPeriodNanos(periodNanos);
    }

    /**
     * Head of {@code stopServer}, before the worlds save: the pool must stop first so the saves read
     * settled state, then every in-flight teleport places so no entity is lost to the shutdown.
     */
    public void haltTicking(MinecraftServer server) {
        scheduler.shutdown();
        globalScheduler.drain();
        for (ServerLevel level : server.getAllLevels()) {
            ((ServerLevelRegionAccess) level).leafs$regions().drainUnloadsForShutdown();
            EntityTeleports teleports = ((ServerLevelEntityAccess) level).leafs$entityTeleports();
            try {
                teleports.completeAll();
            } catch (RuntimeException exception) {
                Leafs.LOGGER.error("Completing pending teleports into {} failed; {} may be lost", level.dimension().identifier(), teleports.pendingCount(), exception);
            }
        }
    }

    public void shutdown(MinecraftServer server) {
        scheduler.shutdown();
        watchdog.stop();
        drainRegions(server);
    }

    /** Region crashes stop the whole server cleanly; the region-scoped report was already written by the tick loop. */
    private void onRegionTickFailure(TickHandle handle, Throwable throwable) {
        Leafs.LOGGER.error("Region tick failed on #{} in {} - stopping the server", handle.id(), handle.dimension(), throwable);
        handle.cancel();
        server.halt(false);
    }

    /**
     * Walks {@code getAllLevels()} rather than the units, which only hold levels that ticked at least
     * once. Never throws: this runs after the worlds are saved, where a crash would only misattribute the shutdown.
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
        return levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level, scheduler));
    }
}
