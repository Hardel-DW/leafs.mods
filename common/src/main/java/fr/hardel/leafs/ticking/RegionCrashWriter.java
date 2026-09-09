package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.metrics.ModAttribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Writes region-scoped crash files; never throws, a crash path must not crash. */
public final class RegionCrashWriter {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private final Path directory;
    private final ModAttribution attribution;

    public RegionCrashWriter(Path directory, ModAttribution attribution) {
        this.directory = directory;
        this.attribution = attribution;
    }

    public void write(RegionCrashReport report, Throwable cause) {
        String content = report.format(attribution, cause);
        Leafs.LOGGER.error("Region crash:\n{}", content);
        try {
            Files.createDirectories(directory);
            Files.writeString(uniqueFile(), content);
        } catch (IOException exception) {
            Leafs.LOGGER.error("Unable to write the region crash report", exception);
        }
    }

    private Path uniqueFile() {
        String stamp = FILE_STAMP.format(LocalDateTime.now());
        Path file = directory.resolve("region-crash-" + stamp + ".txt");
        for (int suffix = 2; Files.exists(file); suffix++) {
            file = directory.resolve("region-crash-" + stamp + "-" + suffix + ".txt");
        }

        return file;
    }
}
