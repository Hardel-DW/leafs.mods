package fr.hardel.leafs.ticking;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ownership.RegionCrashReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Writes region-scoped crash files; never throws - a crash path must not crash. */
public final class RegionCrashWriter {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private final Path directory;

    public RegionCrashWriter(Path directory) {
        this.directory = directory;
    }

    public Path write(RegionCrashReport report, Throwable cause) {
        String content = report.format(cause);
        Leafs.LOGGER.error("Region crash:\n{}", content);
        try {
            Files.createDirectories(directory);
            Path file = uniqueFile();
            Files.writeString(file, content);

            return file;
        } catch (IOException exception) {
            Leafs.LOGGER.error("Unable to write the region crash report", exception);
            return null;
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
