package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.metrics.TickStages.TickStage;
import fr.hardel.leafs.metrics.ServerMetrics;
import fr.hardel.leafs.scheduler.GlobalScheduler;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Server-scoped orchestrator of the region tick machinery, one per server, reached by {@link #of}. */
public final class TickingManager {
    private final MinecraftServer server;
    private final ServerMetrics metrics = new ServerMetrics();
    private final LeafsWatchdog watchdog;
    private final RegionTickScheduler scheduler;
    private final ChunkPool chunkPool;
    private final GlobalScheduler globalScheduler = new GlobalScheduler();
    private final OwnWork serverWork = new OwnWork(this::pumpServer);
    private final Map<ServerLevel, LevelTickUnit> levelUnits = new ConcurrentHashMap<>();
    private final AtomicLong nextUnitId = new AtomicLong(1);
    private volatile boolean globalTicking;
    private final long slowTaskNanos;
    private volatile boolean halted;

    public TickingManager(MinecraftServer server, LeafsConfig config) {
        this.server = server;
        long warnNanos = config.debug().watchdogWarnNanos();
        this.watchdog = new LeafsWatchdog(warnNanos, () -> killAfterNanos(server), now -> ThreadWaits.stalled(now, warnNanos), Leafs.LOGGER::error, new WatchdogKill(server));
        RegionCrashWriter crashWriter = new RegionCrashWriter(Path.of("crash-reports"), Leafs.platform().attribution());
        ThreadGroup serverThreads = Leafs.platform().serverThreads();
        this.scheduler = new RegionTickScheduler(serverThreads, config.effectiveRegionThreads(), config.debug().perRegionLogs(), watchdog, crashWriter, this::onRegionTickFailure);
        this.slowTaskNanos = config.debug().slowTaskNanos();
        this.chunkPool = new ChunkPool(serverThreads, config.effectiveChunkThreads(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT);
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

    /** Above this, a chunk wait or an inbox task is logged with what it was. */
    public long slowTaskNanos() {
        return slowTaskNanos;
    }

    /** True once {@code stopServer} began: the shutdown drains remaining work in line. */
    public boolean halted() {
        return halted;
    }

    public ChunkPool chunkPool() {
        return chunkPool;
    }

    /** The server thread pumping while it waits (managedBlock) also runs the diverted tasks, or a wait on one of them never ends. */
    public boolean pumpDiverted() {
        return globalTicking && server.isSameThread() && globalScheduler.drain();
    }

    /** A wait on Leafs, region or chunk: the server thread runs the tasks the regions handed it and the chunk work of every level, a region the chunk work of its inbox. */
    public void await(BooleanSupplier done) {
        if (server.isSameThread()) {
            serverWork.until(done);
            return;
        }

        WorldTickContext mine = WorldTickContext.current();
        new OwnWork(() -> mine != null && mine.region().data().inbox().drainChunkWork() > 0).until(done);
    }

    private boolean pumpServer() {
        boolean worked = globalScheduler.drain();
        for (ServerLevel level : server.getAllLevels()) {
            worked |= level.getChunkSource().pollTask();
        }

        return worked;
    }

    /** Diverted as long as a Leafs thread lives: past {@code stopped} vanilla runs the task inline on the caller, and its reentrant counter is not thread-safe. The task runs as a head, borrowing at contact like a command. */
    public boolean divertExecute(Runnable task) {
        if (!globalTicking || server.isSameThread()) {
            return false;
        }

        globalScheduler.run(() -> RegionBorrow.hold(borrow -> {
            task.run();
            return null;
        }));
        
        return true;
    }

    public Collection<LevelTickUnit> units() {
        return levelUnits.values();
    }

    public void tickLevel(ServerLevel level, Runnable vanillaTick) {
        globalTicking = true;
        LevelTickUnit unit = levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level, scheduler));
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

    public void tickPausedNetwork() {
        for (LevelTickUnit unit : levelUnits.values()) {
            unit.tickPausedNetwork();
        }
    }

    /** Every region's inbox runs inline, looped because a task can post a follow-up on another level (cross-dimension teleport). */
    private void drainRegionTasks() {
        int drained;
        do {
            drained = 0;
            for (ServerLevel level : server.getAllLevels()) {
                drained += LevelRegions.of(level).drainInboxes();
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
        chunkPool.shutdown();
        for (ServerLevel level : server.getAllLevels()) {
            LevelChunks.of(level).holders().logWaitingTeardowns(level.dimension().identifier().toString());
            Leafs.LOGGER.info("{} graph sections left in {}", LevelChunks.of(level).graphs().sectionCount(), level.dimension().identifier());
        }

        globalTicking = false;
        globalScheduler.drain();
        watchdog.stop();
        drainRegions();
        // A dedicated JVM must now die, so the deadline stays armed until the process exits; in solo the JVM lives on.
        if (!server.isDedicatedServer()) {
            watchdog.disarmShutdownDeadline();
        }
    }

    private void onRegionTickFailure(TickHandle handle, Throwable throwable) {
        Leafs.LOGGER.error("Tick unit #{} in {} threw - stopping the server", handle.id(), handle.dimension(), throwable);
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
}

