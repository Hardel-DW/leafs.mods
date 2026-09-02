package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.core.ChunkWorkers;
import fr.hardel.leafs.metrics.ModAttribution;
import fr.hardel.leafs.metrics.TickStages.TickStage;
import fr.hardel.leafs.metrics.ServerMetrics;
import fr.hardel.leafs.scheduler.GlobalScheduler;
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
    private final MinecraftServer server;
    private final ServerMetrics metrics = new ServerMetrics();
    private final LeafsWatchdog watchdog;
    private final RegionTickScheduler scheduler;
    private final ChunkWorkers chunkWorkers;
    private final GlobalScheduler globalScheduler = new GlobalScheduler();
    private final int slowTaskWarnMillis;
    private final Map<ServerLevel, LevelTickUnit> levelUnits = new ConcurrentHashMap<>();
    private final AtomicLong nextUnitId = new AtomicLong(1);
    private volatile boolean globalTicking;
    private volatile boolean halted;

    public TickingManager(MinecraftServer server, LeafsConfig config) {
        this.server = server;
        this.slowTaskWarnMillis = config.debug().slowTaskWarnMillis();
        this.watchdog = new LeafsWatchdog(Duration.ofSeconds(config.debug().watchdogWarnSeconds()), () -> killAfterNanos(server), Leafs.LOGGER::error, new WatchdogKill(server));
        RegionCrashWriter crashWriter = new RegionCrashWriter(Path.of("crash-reports"), ModAttribution.fromLoader());
        this.scheduler = new RegionTickScheduler(config.effectiveRegionThreads(), config.debug().perRegionLogs(), watchdog, crashWriter, this::onRegionTickFailure);
        this.chunkWorkers = new ChunkWorkers(config.effectiveChunkThreads());
        watchdog.start();
        scheduler.start();
        Leafs.LOGGER.info("Leafs ticking live - {} region workers and {} chunk workers; regions tick free-running, the serial remainder stays on the server thread",
            config.effectiveRegionThreads(), config.effectiveChunkThreads());
    }

    /** Vanilla's {@code max-tick-time}, read late: the dedicated settings bind after this manager is built. Only a dedicated server kills, -1 disables. */
    private static long killAfterNanos(MinecraftServer server) {
        return server instanceof DedicatedServer dedicated && dedicated.getMaxTickLength() > 0 ? Duration.ofMillis(dedicated.getMaxTickLength()).toNanos() : 0L;
    }

    public static TickingManager of(MinecraftServer server) {
        return ((LeafsServerAccess) server).leafs$ticking();
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

    /** True once {@code stopServer} began: the shutdown drains remaining work in line. */
    public boolean halted() {
        return halted;
    }

    public ChunkWorkers chunkWorkers() {
        return chunkWorkers;
    }

    /** The server thread pumping while it waits (managedBlock) also runs the diverted tasks, or a wait on one of them never ends. */
    public boolean pumpDiverted() {
        return globalTicking && server.isSameThread() && globalScheduler.drain();
    }

    /** Diverted as long as a Leafs thread lives: past {@code stopped} vanilla runs the task inline on the caller, and its reentrant counter is not thread-safe. */
    public boolean divertExecute(Runnable task) {
        if (!globalTicking || server.isSameThread()) {
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

    /** A level closing takes its unit and its region handles with it; a mod that unloads a dimension leaves nothing scheduled behind. */
    public void forgetLevel(ServerLevel level) {
        LevelTickUnit unit = levelUnits.remove(level);
        if (unit != null) {
            unit.cancel();
        }

        LevelRegions.of(level).retire();
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

    public void setTickPeriodNanos(long periodNanos) {
        scheduler.setPeriodNanos(periodNanos);
    }

    /** Every chunk's mail runs inline, looped because a mail can post a follow-up on another level (cross-dimension teleport). */
    private void drainRegionTasks() {
        int drained;
        do {
            drained = 0;
            for (ServerLevel level : server.getAllLevels()) {
                drained += RegionChunkAccess.scheduling(level.getChunkSource().chunkMap).mailbox().drainAll();
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
    }

    /** The player saves of {@code removeAll} ran before this point; the flush makes them durable before the JVM exits. The pools are gone, so diversion ends here. */
    public void shutdown() {
        chunkWorkers.shutdown();
        globalTicking = false;
        globalScheduler.drain();
        watchdog.stop();
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
            Leafs.LOGGER.error("Leafs regions NOT drained: {} regions and {} sections outlived the simulation that feeds them", regions, sections);
        }
    }

    private LevelTickUnit unitFor(ServerLevel level) {
        return levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level, scheduler, slowTaskWarnMillis));
    }
}
