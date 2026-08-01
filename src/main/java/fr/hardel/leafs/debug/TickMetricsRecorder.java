package fr.hardel.leafs.debug;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.LevelTickUnit;
import fr.hardel.leafs.ticking.TickTimings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Appends one CSV row per tick unit on a fixed period, from its own thread — the tick path pays
 * nothing. Only the timing rings and the published census are read, both of which tolerate an
 * off-thread reader; nothing here touches world state. Off unless {@code metricsLogSeconds > 0}.
 */
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
            int periodSeconds = LeafsConfig.get().metricsLogSeconds();
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
        for (LevelTickUnit unit : ((LeafsServerAccess) server).leafs$ticking().units()) {
            TickTimings.Snapshot timings = unit.timings().sample(nowNanos);
            writer.write(String.format(Locale.ROOT, "%d,%d,%s,%d,%.2f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d%n",
                epochMillis, unit.id(), unit.dimension(), unit.currentTick(), timings.tps(), timings.msptAverage(),
                timings.mspt50(), timings.mspt95(), timings.mspt99(), timings.msptMax(), unit.chunkCount(), unit.entityCount()));
        }
    }
}
