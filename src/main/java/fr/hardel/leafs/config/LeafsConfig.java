package fr.hardel.leafs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.hardel.leafs.Leafs;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record LeafsConfig(int regionThreads, int gridSectionShift, int mergeRadius, int bufferRadius, int watchdogWarnSeconds, int metricsLogSeconds, boolean compatBarrier, boolean perRegionLogs) {
    public static final int AUTO_THREADS = 0;
    public static final int ALL_CORES = -1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LeafsConfig instance;

    private static final Codec<Integer> REGION_THREADS = Codec.intRange(ALL_CORES, 1024)
        .validate(value -> value == AUTO_THREADS
            ? DataResult.error(() -> "region_threads 0 is invalid: omit the key for auto, -1 for all cores")
            : DataResult.success(value));

    private static final MapCodec<LeafsConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
        REGION_THREADS.optionalFieldOf("region_threads", AUTO_THREADS).forGetter(LeafsConfig::regionThreads),
        Codec.intRange(1, 8).optionalFieldOf("grid_section_shift", 4).forGetter(LeafsConfig::gridSectionShift),
        Codec.intRange(1, 8).optionalFieldOf("merge_radius", 1).forGetter(LeafsConfig::mergeRadius),
        Codec.intRange(1, 8).optionalFieldOf("buffer_radius", 1).forGetter(LeafsConfig::bufferRadius),
        Codec.intRange(1, 600).optionalFieldOf("watchdog_warn_seconds", 15).forGetter(LeafsConfig::watchdogWarnSeconds),
        Codec.intRange(0, 3600).optionalFieldOf("metrics_log_seconds", 0).forGetter(LeafsConfig::metricsLogSeconds),
        Codec.BOOL.optionalFieldOf("compat_barrier", true).forGetter(LeafsConfig::compatBarrier),
        Codec.BOOL.optionalFieldOf("per_region_logs", true).forGetter(LeafsConfig::perRegionLogs)
    ).apply(builder, LeafsConfig::new));

    public static final Codec<LeafsConfig> CODEC = MAP_CODEC.codec();

    public static void load() {
        instance = load(FabricLoader.getInstance().getConfigDir().resolve(Leafs.MOD_ID + ".json"));
    }

    public static LeafsConfig get() {
        if (instance == null) {
            throw new IllegalStateException("Leafs config accessed before mod initialisation");
        }

        return instance;
    }

    public static LeafsConfig defaults() {
        return CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
    }

    static LeafsConfig load(Path file) {
        try {
            if (Files.notExists(file)) {
                LeafsConfig defaults = defaults();
                JsonElement encoded = CODEC.encodeStart(JsonOps.INSTANCE, defaults).getOrThrow();
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(encoded) + System.lineSeparator());
                return defaults;
            }

            JsonElement json = JsonParser.parseString(Files.readString(file));
            if (json instanceof JsonObject object) {
                List<String> valid = MAP_CODEC.keys(JsonOps.INSTANCE).map(JsonElement::getAsString).toList();
                for (String key : object.keySet()) {
                    if (!valid.contains(key)) {
                        throw new IllegalArgumentException("Config " + file + " has unknown key \"" + key + "\", valid keys: " + valid);
                    }
                }
            }

            return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new IllegalArgumentException("Config " + file + " is invalid: " + error));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Config " + file + " is not valid JSON", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read or create " + file, exception);
        }
    }

    public int effectiveRegionThreads() {
        return regionThreads > 0 ? regionThreads : Runtime.getRuntime().availableProcessors();
    }

    public int sectionChunkSize() {
        return 1 << gridSectionShift;
    }
}
