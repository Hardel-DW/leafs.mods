package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.core.ChunkWorkers;
import fr.hardel.leafs.global.DeferredFileWrites;
import fr.hardel.leafs.metrics.TickStages.TickStage;
import fr.hardel.leafs.metrics.ServerMetrics;
import fr.hardel.leafs.scheduler.GlobalScheduler;
import fr.hardel.leafs.scheduler.RegionScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Server-scoped orchestrator of the region tick machinery, one per server, reached by {@link #of}. */
public final class TickingManager {
    private static final int QUIESCE_DRAIN_FLOOR = 64;

    private final MinecraftServer server;
    private final ServerMetrics metrics = new ServerMetrics();
    private final TickBarrier barrier = new TickBarrier();
    private final LeafsWatchdog watchdog;
    private final RegionTickScheduler scheduler;
    private final ChunkWorkers chunkWorkers;
    private final GlobalScheduler globalScheduler = new GlobalScheduler();
    private final SerialWorkBudget serialBudget = new SerialWorkBudget();
    private final int slowTaskWarnMillis;
    private final Map<ServerLevel, LevelTickUnit> levelUnits = new ConcurrentHashMap<>();
    private final AtomicLong nextUnitId = new AtomicLong(1);
    private volatile boolean globalTicking;
    private volatile boolean halted;

    public TickingManager(MinecraftServer server, LeafsConfig config) {
        this.server = server;
        this.slowTaskWarnMillis = config.debug().slowTaskWarnMillis();
        this.watchdog = new LeafsWatchdog(Duration.ofSeconds(config.debug().watchdogWarnSeconds()), () -> killAfterNanos(server), Leafs.LOGGER::error, new WatchdogKill(server));
        RegionCrashWriter crashWriter = new RegionCrashWriter(Path.of("crash-reports"));
        this.scheduler = new RegionTickScheduler(config.effectiveThreads(), config.debug().perRegionLogs(), barrier, watchdog, crashWriter, this::onRegionTickFailure);
        this.chunkWorkers = new ChunkWorkers(config.effectiveThreads());
        DeferredFileWrites.start();
        watchdog.start();
        scheduler.start();
        Leafs.LOGGER.info("Leafs ticking live - {} region workers and as many chunk workers; regions tick free-running, the serial remainder stays on the server thread", config.effectiveThreads());
    }

    /** Vanilla's {@code max-tick-time}, read late: the dedicated settings bind after this manager is built. Only a dedicated server kills, -1 disables. */
    private static long killAfterNanos(MinecraftServer server) {
        return server instanceof DedicatedServer dedicated && dedicated.getMaxTickLength() > 0 ? Duration.ofMillis(dedicated.getMaxTickLength()).toNanos() : 0L;
    }

    public static TickingManager of(MinecraftServer server) {
        return ((LeafsServerAccess) server).leafs$ticking();
    }

    public TickBarrier barrier() {
        return barrier;
    }

    public ServerMetrics metrics() {
        return metrics;
    }

    public RegionTickScheduler scheduler() {
        return scheduler;
    }

    /** The unit of a level that ticked at least once; null before, so callers sampling metrics stay no-ops. */
    public LevelTickUnit unitOf(ServerLevel level) {
        return levelUnits.get(level);
    }

    /** Serial stage boundary reached inside the vanilla remainder; ignored while no serial tick is in flight. */
    public void markSerial(ServerLevel level, TickStage stage) {
        LevelTickUnit unit = levelUnits.get(level);
        if (unit != null) {
            unit.stages().mark(stage);
        }
    }

    public GlobalScheduler globalScheduler() {
        return globalScheduler;
    }

    public SerialWorkBudget serialBudget() {
        return serialBudget;
    }

    /** True once {@code stopServer} began: budget deferrals stop, the shutdown drains remaining work in line. */
    public boolean halted() {
        return halted;
    }

    public ChunkWorkers chunkWorkers() {
        return chunkWorkers;
    }

    /** Once regions may be live, an off-thread {@code MinecraftServer.execute} lands in the global phase. Hot path, logs nothing. */
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

    /** Ticket ops first, then bookkeeping (which runs the distance updates that materialise holders), then offers. */
    public void quiesce() {
        for (ServerLevel level : server.getAllLevels()) {
            LevelRegions regions = LevelRegions.of(level);
            drainChunkBookkeeping(level);
            RegionScheduler<RegionTickData> taskScheduler = regions.taskScheduler();
            if (taskScheduler != null) {
                taskScheduler.completePending();
            }

            regions.rethrowFeedFailure();
        }
    }

    /** Time-boxed on the shared budget above a floor; a synchronous waiter drains the pump itself, so a leftover never blocks. A shutdown drains whole. */
    private void drainChunkBookkeeping(ServerLevel level) {
        int drained = 0;
        boolean hasMore = true;
        while (hasMore) {
            hasMore = level.getChunkSource().pollTask();
            if (hasMore && !halted && ++drained >= QUIESCE_DRAIN_FLOOR && serialBudget.expired(System.nanoTime())) {
                return;
            }
        }
    }

    public void setTickPeriodNanos(long periodNanos) {
        scheduler.setPeriodNanos(periodNanos);
    }

    /** Queued region tasks run inline, looped because a draining task can queue a follow-up on another level (cross-dimension teleport). */
    private void drainRegionTasks() {
        int drained;
        do {
            drained = 0;
            for (ServerLevel level : server.getAllLevels()) {
                drained += LevelRegions.of(level).drainTasksInline();
            }
        } while (drained > 0);
    }

    /** Head of {@code stopServer}: the deadline arms first so a wedged stop still dies, then the pool stops and the region lanes drain before the saves. */
    public void haltTicking() {
        watchdog.armShutdownDeadline(LeafsWatchdog.SHUTDOWN_DEADLINE);
        scheduler.shutdown();
        halted = true;
        drainRegionTasks();
        globalScheduler.drain();
        for (ServerLevel level : server.getAllLevels()) {
            LevelRegions.of(level).drainUnloadsForShutdown();
        }
    }

    /** The player saves of {@code removeAll} ran before this point; the flush makes them durable before the JVM exits. */
    public void shutdown() {
        chunkWorkers.shutdown();
        watchdog.stop();
        DeferredFileWrites.stopAndFlush();
        drainRegions();
        // A dedicated JVM must now die, so the deadline stays armed until the process exits; in solo the JVM lives on.
        if (!server.isDedicatedServer()) {
            watchdog.disarmShutdownDeadline();
        }
    }

    /** Only an unrecoverable unit lands here, the server-thread unit or a region dead twice in a minute; the report is already written. */
    private void onRegionTickFailure(TickHandle handle, Throwable throwable) {
        Leafs.LOGGER.error("Tick unit #{} in {} is not recoverable - stopping the server", handle.id(), handle.dimension(), throwable);
        handle.cancel();
        server.halt(false);
    }

    /** Walks every level, not only the ticked units. Never throws, the worlds are already saved. */
    private void drainRegions() {
        int regions = 0;
        int sections = 0;
        for (ServerLevel level : server.getAllLevels()) {
            LevelRegions levelRegions = LevelRegions.of(level);
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
        return levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level, scheduler, serialBudget, slowTaskWarnMillis));
    }
}
