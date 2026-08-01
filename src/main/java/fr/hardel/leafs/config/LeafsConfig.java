package fr.hardel.leafs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import fr.hardel.leafs.Leafs;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Immutable configuration, loaded once at bootstrap. The fields are the schema: Gson maps them from
 * {@code config/leafs.json}, absent keys keep their default, invalid values fail the boot loudly.
 */
public final class LeafsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int AUTO_THREADS = 0;

    private static LeafsConfig instance;

    private int regionThreads = AUTO_THREADS;
    private int gridSectionShift = 4;
    private int mergeRadius = 1;
    private int bufferRadius = 1;
    private int watchdogWarnSeconds = 60;
    private boolean compatBarrier = true;
    private boolean perRegionLogs = true;

    private LeafsConfig() {
    }

    public static void load() {
        instance = load(FabricLoader.getInstance().getConfigDir().resolve(Leafs.MOD_ID + ".json"));
    }

    public static LeafsConfig get() {
        if (instance == null) {
            throw new IllegalStateException("Leafs config accessed before mod initialisation");
        }

        return instance;
    }

    static LeafsConfig load(Path file) {
        try {
            if (Files.notExists(file)) {
                LeafsConfig defaults = defaults();
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(defaults) + System.lineSeparator());
                return defaults;
            }

            LeafsConfig config = GSON.fromJson(Files.readString(file), LeafsConfig.class);
            if (config == null)
                throw new IllegalArgumentException("Config " + file + " is empty");

            return config.validated();
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Config " + file + " is not valid JSON", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read or create " + file, exception);
        }
    }

    static LeafsConfig defaults() {
        return new LeafsConfig();
    }

    public int regionThreads() {
        return regionThreads;
    }

    public int gridSectionShift() {
        return gridSectionShift;
    }

    public int mergeRadius() {
        return mergeRadius;
    }

    public int bufferRadius() {
        return bufferRadius;
    }

    public int watchdogWarnSeconds() {
        return watchdogWarnSeconds;
    }

    public boolean compatBarrier() {
        return compatBarrier;
    }

    public boolean perRegionLogs() {
        return perRegionLogs;
    }

    public int effectiveRegionThreads() {
        return regionThreads == AUTO_THREADS ? Runtime.getRuntime().availableProcessors() : regionThreads;
    }

    public int sectionChunkSize() {
        return 1 << gridSectionShift;
    }

    private LeafsConfig validated() {
        requireRange("regionThreads", regionThreads, AUTO_THREADS, 1024);
        requireRange("gridSectionShift", gridSectionShift, 1, 8);
        requireRange("mergeRadius", mergeRadius, 1, 8);
        requireRange("bufferRadius", bufferRadius, 1, 8);
        requireRange("watchdogWarnSeconds", watchdogWarnSeconds, 1, 600);
        return this;
    }

    private static void requireRange(String key, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Config '" + key + "' must be in [" + min + ", " + max + "], got " + value);
        }
    }
}
