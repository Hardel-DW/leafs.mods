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
import java.util.Arrays;
import java.util.Optional;
import java.util.function.ToIntFunction;

public record LeafsConfig(int maxThreads, int sectionSize, int regionMergeDistance, int regionBufferDistance, Debug debug) {
    public static final int ALL_CORES = -1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LeafsConfig instance;
    private static Path file;

    public record Debug(int watchdogWarnSeconds, boolean perRegionLogs, int slowTaskWarnMillis) {
    }

    /** The keys {@code /leafs config} may rewrite; the debug group stays a file-only matter. */
    public enum Setting {
        MAX_THREADS("max_threads", LeafsConfig::maxThreads),
        SECTION_SIZE("section_size", LeafsConfig::sectionSize),
        REGION_MERGE_DISTANCE("region_merge_distance", LeafsConfig::regionMergeDistance),
        REGION_BUFFER_DISTANCE("region_buffer_distance", LeafsConfig::regionBufferDistance);

        private final String key;
        private final ToIntFunction<LeafsConfig> getter;

        Setting(String key, ToIntFunction<LeafsConfig> getter) {
            this.key = key;
            this.getter = getter;
        }

        public String key() {
            return key;
        }

        public int read(LeafsConfig config) {
            return getter.applyAsInt(config);
        }

        public static Optional<Setting> byKey(String key) {
            return Arrays.stream(values()).filter(setting -> setting.key.equals(key)).findFirst();
        }
    }

    private static final Codec<Integer> MAX_THREADS = Codec.intRange(ALL_CORES, 1024)
        .validate(value -> value == 0
            ? DataResult.error(() -> "max_threads 0 is invalid: -1 uses all cores")
            : DataResult.success(value));

    private static final Codec<Integer> SECTION_SIZE = Codec.intRange(2, 256)
        .validate(value -> Integer.bitCount(value) == 1
            ? DataResult.success(value)
            : DataResult.error(() -> "section_size must be a power of two: 2, 4, 8, 16, 32, 64, 128 or 256"));

    private static final MapCodec<Debug> DEBUG = RecordCodecBuilder.mapCodec(builder -> builder.group(
        Codec.intRange(1, 600).optionalFieldOf("watchdog_warn_seconds", 15).forGetter(Debug::watchdogWarnSeconds),
        Codec.BOOL.optionalFieldOf("per_region_logs", false).forGetter(Debug::perRegionLogs),
        Codec.intRange(0, 60_000).optionalFieldOf("slow_task_warn_millis", 50).forGetter(Debug::slowTaskWarnMillis)
    ).apply(builder, Debug::new));

    private static final Codec<LeafsConfig> CODEC = RecordCodecBuilder.create(builder -> builder.group(
        MAX_THREADS.optionalFieldOf(Setting.MAX_THREADS.key(), ALL_CORES).forGetter(LeafsConfig::maxThreads),
        SECTION_SIZE.optionalFieldOf(Setting.SECTION_SIZE.key(), 16).forGetter(LeafsConfig::sectionSize),
        Codec.intRange(1, 8).optionalFieldOf(Setting.REGION_MERGE_DISTANCE.key(), 1).forGetter(LeafsConfig::regionMergeDistance),
        Codec.intRange(1, 8).optionalFieldOf(Setting.REGION_BUFFER_DISTANCE.key(), 1).forGetter(LeafsConfig::regionBufferDistance),
        DEBUG.codec().optionalFieldOf("debug", defaultsOf(DEBUG.codec())).forGetter(LeafsConfig::debug)
    ).apply(builder, LeafsConfig::new));

    public static void register() {
        file = FabricLoader.getInstance().getConfigDir().resolve(Leafs.MOD_ID + ".json");
        instance = load(file);
    }

    public static LeafsConfig get() {
        if (instance == null) {
            throw new IllegalStateException("Leafs config accessed before mod initialisation");
        }

        return instance;
    }

    public static LeafsConfig defaults() {
        return defaultsOf(CODEC);
    }

    /** A missing file is written with the defaults; unknown keys are ignored, an invalid value stops the boot naming the file. */
    static LeafsConfig load(Path file) {
        return Files.notExists(file) ? write(file, defaults()) : parse(file, read(file));
    }

    /** One key rewritten through the same codec as the load, so the same ranges apply; live at the next start. */
    public static LeafsConfig rewrite(Setting setting, int value) {
        JsonObject json = encode(get());
        json.addProperty(setting.key(), value);
        return write(file, parse(file, json));
    }

    public int effectiveThreads() {
        return maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();
    }

    public int sectionShift() {
        return Integer.numberOfTrailingZeros(sectionSize);
    }

    private static <T> T defaultsOf(Codec<T> codec) {
        return codec.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
    }

    private static JsonObject encode(LeafsConfig config) {
        return CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow().getAsJsonObject();
    }

    private static LeafsConfig parse(Path file, JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new IllegalArgumentException("Config " + file + " is invalid: " + error));
    }

    private static JsonElement read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Config " + file + " is not valid JSON", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + file, exception);
        }
    }

    private static LeafsConfig write(Path file, LeafsConfig config) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(encode(config)) + System.lineSeparator());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write " + file, exception);
        }

        return config;
    }
}
