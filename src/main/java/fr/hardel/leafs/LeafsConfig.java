package fr.hardel.leafs;

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
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record LeafsConfig(int maxThreads, int sectionSize, int regionMergeDistance, int regionBufferDistance, Debug debug) {
    public static final int ALL_CORES = -1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LeafsConfig instance;

    public record Debug(int watchdogWarnSeconds, boolean perRegionLogs, int slowTaskWarnMillis) {
    }

    private static final Codec<Integer> MAX_THREADS = Codec.intRange(ALL_CORES, 1024)
        .validate(value -> value == 0
            ? DataResult.error(() -> "max_threads 0 is invalid: -1 uses all cores")
            : DataResult.success(value));

    private static final Codec<Integer> SECTION_SIZE = Codec.intRange(2, 256)
        .validate(value -> Integer.bitCount(value) == 1
            ? DataResult.success(value)
            : DataResult.error(() -> "section_size must be a power of two: 2, 4, 8, 16, 32, 64, 128 or 256"));

    private static final MapCodec<Debug> DEBUG_MAP = RecordCodecBuilder.mapCodec(builder -> builder.group(
        Codec.intRange(1, 600).optionalFieldOf("watchdog_warn_seconds", 15).forGetter(Debug::watchdogWarnSeconds),
        Codec.BOOL.optionalFieldOf("per_region_logs", false).forGetter(Debug::perRegionLogs),
        Codec.intRange(0, 60_000).optionalFieldOf("slow_task_warn_millis", 50).forGetter(Debug::slowTaskWarnMillis)
    ).apply(builder, Debug::new));

    private static final Codec<Debug> DEBUG = DEBUG_MAP.codec();

    private static final MapCodec<LeafsConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
        MAX_THREADS.optionalFieldOf("max_threads", ALL_CORES).forGetter(LeafsConfig::maxThreads),
        SECTION_SIZE.optionalFieldOf("section_size", 16).forGetter(LeafsConfig::sectionSize),
        Codec.intRange(1, 8).optionalFieldOf("region_merge_distance", 1).forGetter(LeafsConfig::regionMergeDistance),
        Codec.intRange(1, 8).optionalFieldOf("region_buffer_distance", 1).forGetter(LeafsConfig::regionBufferDistance),
        DEBUG.optionalFieldOf("debug", groupDefaults(DEBUG_MAP)).forGetter(LeafsConfig::debug)
    ).apply(builder, LeafsConfig::new));

    private static final Codec<LeafsConfig> CODEC = MAP_CODEC.codec();

    public static void register() {
        instance = load(FabricLoader.getInstance().getConfigDir().resolve(Leafs.MOD_ID + ".json"));
    }

    public static LeafsConfig get() {
        if (instance == null) {
            throw new IllegalStateException("Leafs config accessed before mod initialisation");
        }

        return instance;
    }

    public static LeafsConfig defaults() {
        return groupDefaults(MAP_CODEC);
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
                requireKnownKeys(file, object, MAP_CODEC);
                if (object.get("debug") instanceof JsonObject debug) {
                    requireKnownKeys(file, debug, DEBUG_MAP);
                }
            }

            return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new IllegalArgumentException("Config " + file + " is invalid: " + error));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Config " + file + " is not valid JSON", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read or create " + file, exception);
        }
    }

    public int effectiveThreads() {
        return maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();
    }

    public int sectionShift() {
        return Integer.numberOfTrailingZeros(sectionSize);
    }

    private static <T> T groupDefaults(MapCodec<T> codec) {
        return codec.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
    }

    private static void requireKnownKeys(Path file, JsonObject object, MapCodec<?> codec) {
        List<String> valid = codec.keys(JsonOps.INSTANCE).map(JsonElement::getAsString).distinct().toList();
        for (String key : object.keySet()) {
            if (!valid.contains(key)) {
                throw new IllegalArgumentException("Config " + file + " has unknown key \"" + key + "\", valid keys: " + valid);
            }
        }
    }
}
