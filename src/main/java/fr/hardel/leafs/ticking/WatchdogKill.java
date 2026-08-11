package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.ServerWatchdog;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The kill side of the watchdog: dumps every thread into a vanilla-style crash report, then halts
 * the JVM. {@code Runtime.halt} because a wedged server cannot be trusted to run its shutdown path,
 * the lesson of the arret-bloque ticket.
 */
public final class WatchdogKill implements Consumer<LeafsWatchdog.Stall> {
    private final MinecraftServer server;

    public WatchdogKill(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void accept(LeafsWatchdog.Stall stall) {
        Leafs.LOGGER.error("{} - dumping all threads and killing the server", stall.summary());
        CrashReport report = ServerWatchdog.createWatchdogCrashReport(stall.summary(), stall.thread().threadId());
        server.fillSystemReport(report.getSystemReport());
        Bootstrap.realStdoutPrintln("Leafs watchdog crash report:\n" + report.getFriendlyReport(ReportType.CRASH));
        Path file = server.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-leafs-watchdog.txt");
        if (report.saveToFile(file, ReportType.CRASH)) {
            Leafs.LOGGER.error("The watchdog crash report is saved to {}", file.toAbsolutePath());
        }

        Runtime.getRuntime().halt(1);
    }
}
