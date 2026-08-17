package fr.hardel.leafs.debug;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.LevelTickUnit;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.RegionTickHandle;
import fr.hardel.leafs.ticking.TickTimings;
import fr.hardel.leafs.ticking.TickingManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** CSV metrics recorder: one row per tick unit on a fixed period. Off unless {@code metricsLogSeconds > 0}. */
public final class TickMetricsRecorder {
    private static final Path FILE = Path.of("logs", "leafs-metrics.csv");
    private static final String HEADER = "epochMillis,unitId,dimension,tick,tps,msptAvg,mspt50,mspt95,mspt99,msptMax,chunks,entities";

    private final MinecraftServer server;
    private final long periodMillis;
    private final Thread thread;
    private volatile boolean running = true;

    private TickMetricsRecorder(MinecraftServer server, int periodSeconds) {
        this.server = server;
        this.periodMillis = periodSeconds * 1000L;
        this.thread = new Thread(this::run, "Leafs Metrics Recorder");
        this.thread.setDaemon(true);
    }

    /** Bound to the server lifecycle from the entry point, so ticking/ never depends on debug/. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            int periodSeconds = LeafsConfig.get().debug().metricsLogSeconds();
            if (periodSeconds > 0) {
                new TickMetricsRecorder(server, periodSeconds).start();
            }
        });
    }

    private void start() {
        ServerLifecycleEvents.SERVER_STOPPING.register(_ -> running = false);
        thread.start();
        Leafs.LOGGER.info("Leafs metrics recording to {} every {}s", FILE, periodMillis / 1000L);
    }

    private void run() {
        try (Writer writer = openAppending()) {
            while (running) {
                Thread.sleep(periodMillis);
                writeSample(writer);
                writer.flush();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            Leafs.LOGGER.error("Leafs metrics recording stopped: {} is not writable", FILE, exception);
        }
    }

    private Writer openAppending() throws IOException {
        Files.createDirectories(FILE.getParent());
        boolean fresh = Files.notExists(FILE) || Files.size(FILE) == 0;
        Writer writer = Files.newBufferedWriter(FILE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (fresh) {
            writer.write(HEADER);
            writer.write(System.lineSeparator());
        }

        return writer;
    }

    private void writeSample(Writer writer) throws IOException {
        long epochMillis = System.currentTimeMillis();
        long nowNanos = System.nanoTime();
        for (LevelTickUnit unit : TickingManager.of(server).units()) {
            writeRow(writer, epochMillis, nowNanos, "L" + unit.id(), unit.dimension(), unit.currentTick(),
                unit.timings(), unit.chunkCount(), unit.entityCount());
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Region<RegionTickData> region : LevelRegions.of(level).regionizer().regionsView()) {
                RegionTickHandle handle = region.data().handle();
                if (handle != null && !handle.isCancelled()) {
                    writeRow(writer, epochMillis, nowNanos, "R" + handle.id(), handle.dimension(), handle.currentTick(),
                        handle.timings(), handle.chunkCount(), handle.entityCount());
                }
            }
        }
    }

    private static void writeRow(Writer writer, long epochMillis, long nowNanos, String unitId, String dimension, long tick, TickTimings timings, int chunks, int entities) throws IOException {
        TickTimings.Snapshot snapshot = timings.sample(nowNanos);
        writer.write(String.format(Locale.ROOT, "%d,%s,%s,%d,%.2f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d%n",
            epochMillis, unitId, dimension, tick, snapshot.tps(), snapshot.msptAverage(),
            snapshot.mspt50(), snapshot.mspt95(), snapshot.mspt99(), snapshot.msptMax(), chunks, entities));
    }
}
