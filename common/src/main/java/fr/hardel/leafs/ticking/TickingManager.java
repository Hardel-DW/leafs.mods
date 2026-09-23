package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.pool.ChunkPool;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.metrics.TickStages.TickStage;
import fr.hardel.leafs.metrics.ServerMetrics;
import fr.hardel.leafs.network.RegionNetworkTick;
import fr.hardel.leafs.scheduler.GlobalScheduler;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class TickingManager {
    private final MinecraftServer server;
    private final ServerMetrics metrics = new ServerMetrics();
    private final LeafsWatchdog watchdog;
    private final RegionTickScheduler scheduler;
    private final ChunkPool chunkPool;
    private final GlobalScheduler globalScheduler = new GlobalScheduler(task -> RegionBorrow.hold(_ -> {
        task.run();
        return null;
    }));
    private final OwnWork serverWork = new OwnWork(this::pumpServer);
    private final Map<ServerLevel, LevelTickUnit> levelUnits = new ConcurrentHashMap<>();
    private final AtomicLong nextUnitId = new AtomicLong(1);
    private final AtomicReference<CrashReport> regionCrash = new AtomicReference<>();
    private volatile boolean globalTicking;
    private final long slowTaskNanos;
    private volatile boolean halted;
    private volatile boolean paused;
    private volatile boolean crashed;

    public TickingManager(MinecraftServer server, LeafsConfig config) {
        this.server = server;
        long warnNanos = config.debug().watchdogWarnNanos();
        this.watchdog = new LeafsWatchdog(warnNanos, () -> killAfterNanos(server), now -> ThreadWaits.stalled(now, warnNanos), Leafs.LOGGER::error, new WatchdogKill(server));
        ThreadGroup serverThreads = Leafs.serverThreads();
        this.scheduler = new RegionTickScheduler(serverThreads, config.effectiveRegionThreads(), () -> server.tickRateManager().nanosecondsPerTick(),
            config.debug().perRegionLogs(), watchdog, this::onRegionTickFailure);
        this.slowTaskNanos = config.debug().slowTaskNanos();
        this.chunkPool = new ChunkPool(serverThreads, config.effectiveChunkThreads(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT, this::onChunkTaskFailure);
        watchdog.start();
        scheduler.start();
        Leafs.LOGGER.info("Leafs ticking live - {} region workers and {} chunk workers; regions tick free-running, the serial remainder stays on the server thread",
            config.effectiveRegionThreads(), config.effectiveChunkThreads());
    }

    private static long killAfterNanos(MinecraftServer server) {
        return server instanceof DedicatedServer dedicated && dedicated.getMaxTickLength() > 0 ? Duration.ofMillis(dedicated.getMaxTickLength()).toNanos() : 0L;
    }

    // Used by the Leafs Debug mod
    public static TickingManager of(MinecraftServer server) {
        return ((LeafsServerAccess) server).leafs$ticking();
    }

    // Used by the Leafs Debug mod
    public ServerMetrics metrics() {
        return metrics;
    }

    // Used by the Leafs Debug mod
    public RegionTickScheduler scheduler() {
        return scheduler;
    }

    public LevelTickUnit unitOf(ServerLevel level) {
        return levelUnits.get(level);
    }

    public void markSerial(ServerLevel level, TickStage stage) {
        LevelTickUnit unit = levelUnits.get(level);
        if (unit != null) {
            unit.stages().mark(stage);
        }
    }

    public GlobalScheduler globalScheduler() {
        return globalScheduler;
    }

    public long slowTaskNanos() {
        return slowTaskNanos;
    }

    public boolean halted() {
        return halted;
    }

    public boolean paused() {
        return paused;
    }

    public void throwRegionCrash() {
        CrashReport crash = regionCrash.get();
        if (crash != null) {
            throw new ReportedException(crash);
        }
    }

    public void endServerTick(boolean ticked) {
        paused = !ticked;
        scheduler.wakeMissed();
    }

    // Used by the Leafs Debug mod
    public ChunkPool chunkPool() {
        return chunkPool;
    }

    public boolean onServerThread() {
        return Thread.currentThread() == server.getRunningThread();
    }

    public boolean pumpDiverted() {
        return globalTicking && onServerThread() && globalScheduler.drain();
    }

    public void await(BooleanSupplier done) {
        if (onServerThread()) {
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

    public boolean divertExecute(Runnable task) {
        if (!globalTicking || onServerThread()) {
            return false;
        }

        if (RegionTickScheduler.onWorker()) {
            task.run();
            return true;
        }

        globalScheduler.run(task);
        return true;
    }

    // Used by the Leafs Debug mod
    public Collection<LevelTickUnit> units() {
        return levelUnits.values();
    }

    public void tickLevel(ServerLevel level, Runnable vanillaTick) {
        globalTicking = true;
        levelUnits.computeIfAbsent(level, _ -> new LevelTickUnit(nextUnitId.getAndIncrement(), level, scheduler)).tick(vanillaTick);
    }

    public void forgetLevel(ServerLevel level) {
        levelUnits.remove(level);
        LevelRegions.of(level).retire();
    }

    public void tickPausedNetwork() {
        levelUnits.keySet().forEach(RegionNetworkTick::drainPaused);
    }

    private void drainRegionTasks() {
        int drained;
        do {
            drained = 0;
            for (ServerLevel level : server.getAllLevels()) {
                drained += LevelRegions.of(level).drainInboxes();
            }
        } while (drained > 0);
    }

    public void haltTicking() {
        scheduler.shutdown(crashed, serverWork);
        halted = true;
        drainRegionTasks();
        globalScheduler.drain();
    }

    public void shutdown() {
        chunkPool.shutdown();
        globalTicking = false;
        globalScheduler.drain();
        watchdog.stop();
    }

    private void onChunkTaskFailure(ChunkTask task, Throwable failure) {
        Leafs.LOGGER.error("Chunk task {} failed on {} - stopping the server", task, Thread.currentThread().getName(), failure);
        crashed = true;
        BlockableEventLoop.relayDelayCrash(CrashReport.forThrowable(failure, "Leafs chunk task " + task));
    }

    private void onRegionTickFailure(TickHandle handle, Throwable failure) {
        handle.cancel();
        crashed = true;
        CrashReport report = failure instanceof ReportedException reported ? reported.getReport() : CrashReport.forThrowable(failure, "Ticking Leafs region");
        handle.fillCrashReportCategory(report.addCategory("Leafs region").setDetail("Thread", Thread.currentThread().getName()));
        if (!regionCrash.compareAndSet(null, report)) {
            regionCrash.get().getException().addSuppressed(report.getException());
        }
    }
}

