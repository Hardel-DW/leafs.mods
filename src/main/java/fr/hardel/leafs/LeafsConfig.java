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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.MobCategory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

public record LeafsConfig(int regionThreads, int chunkThreads, int sectionSize, int regionMergeDistance, int regionBufferDistance, int playerChunkLoadsPerTick, Debug debug, Gameplay gameplay) {
    public static final int ALL_CORES = -1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LeafsConfig instance;
    private static Path file;

    public record Debug(int watchdogWarnSeconds, boolean perRegionLogs, int slowTaskWarnMillis) {
    }

    public record Gameplay(MobCapScope mobCapScope, Map<MobCategory, Integer> mobCap) {
        public int mobCap(MobCategory category) {
            return mobCap.getOrDefault(category, category.getMaxInstancesPerChunk());
        }
    }

    public enum MobCapScope implements StringRepresentable {
        LEVEL("level"),
        REGION("region");

        private final String name;

        MobCapScope(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Setting {
        REGION_THREADS("region_threads", LeafsConfig::regionThreads),
        CHUNK_THREADS("chunk_threads", LeafsConfig::chunkThreads),
        SECTION_SIZE("section_size", LeafsConfig::sectionSize),
        REGION_MERGE_DISTANCE("region_merge_distance", LeafsConfig::regionMergeDistance),
        REGION_BUFFER_DISTANCE("region_buffer_distance", LeafsConfig::regionBufferDistance),
        PLAYER_CHUNK_LOADS_PER_TICK("player_chunk_loads_per_tick", LeafsConfig::playerChunkLoadsPerTick);

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

    private static Codec<Integer> threads(Setting setting) {
        return Codec.intRange(ALL_CORES, 1024)
            .validate(value -> value == 0
                ? DataResult.error(() -> setting.key() + " 0 is invalid: -1 uses all cores")
                : DataResult.success(value));
    }

    private static final Codec<Integer> SECTION_SIZE = Codec.intRange(2, 256)
        .validate(value -> Integer.bitCount(value) == 1
            ? DataResult.success(value)
            : DataResult.error(() -> "section_size must be a power of two: 2, 4, 8, 16, 32, 64, 128 or 256"));

    private static final MapCodec<Debug> DEBUG = RecordCodecBuilder.mapCodec(builder -> builder.group(
        Codec.intRange(1, 600).optionalFieldOf("watchdog_warn_seconds", 15).forGetter(Debug::watchdogWarnSeconds),
        Codec.BOOL.optionalFieldOf("per_region_logs", false).forGetter(Debug::perRegionLogs),
        Codec.intRange(0, 60_000).optionalFieldOf("slow_task_warn_millis", 50).forGetter(Debug::slowTaskWarnMillis)
    ).apply(builder, Debug::new));

    private static final MapCodec<Gameplay> GAMEPLAY = RecordCodecBuilder.mapCodec(builder -> builder.group(
        StringRepresentable.fromEnum(MobCapScope::values).optionalFieldOf("mob_cap_scope", MobCapScope.LEVEL).forGetter(Gameplay::mobCapScope),
        Codec.unboundedMap(MobCategory.CODEC, Codec.intRange(0, 100_000)).optionalFieldOf("mob_cap", vanillaMobCaps()).forGetter(Gameplay::mobCap)
    ).apply(builder, Gameplay::new));

    private static final Codec<LeafsConfig> CODEC = RecordCodecBuilder.create(builder -> builder.group(
        threads(Setting.REGION_THREADS).optionalFieldOf(Setting.REGION_THREADS.key(), ALL_CORES).forGetter(LeafsConfig::regionThreads),
        threads(Setting.CHUNK_THREADS).optionalFieldOf(Setting.CHUNK_THREADS.key(), ALL_CORES).forGetter(LeafsConfig::chunkThreads),
        SECTION_SIZE.optionalFieldOf(Setting.SECTION_SIZE.key(), 2).forGetter(LeafsConfig::sectionSize),
        Codec.intRange(1, 8).optionalFieldOf(Setting.REGION_MERGE_DISTANCE.key(), 1).forGetter(LeafsConfig::regionMergeDistance),
        Codec.intRange(1, 8).optionalFieldOf(Setting.REGION_BUFFER_DISTANCE.key(), 1).forGetter(LeafsConfig::regionBufferDistance),
        Codec.intRange(1, 1000).optionalFieldOf(Setting.PLAYER_CHUNK_LOADS_PER_TICK.key(), 5).forGetter(LeafsConfig::playerChunkLoadsPerTick),
        DEBUG.codec().optionalFieldOf("debug", defaultsOf(DEBUG.codec())).forGetter(LeafsConfig::debug),
        GAMEPLAY.codec().optionalFieldOf("gameplay", defaultsOf(GAMEPLAY.codec())).forGetter(LeafsConfig::gameplay)
    ).apply(builder, LeafsConfig::new));

    private static Map<MobCategory, Integer> vanillaMobCaps() {
        Map<MobCategory, Integer> caps = new EnumMap<>(MobCategory.class);
        for (MobCategory category : MobCategory.values()) {
            if (category != MobCategory.MISC) {
                caps.put(category, category.getMaxInstancesPerChunk());
            }
        }

        return caps;
    }

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

    static LeafsConfig load(Path file) {
        return Files.notExists(file) ? write(file, defaults()) : parse(file, read(file));
    }

    public static LeafsConfig rewrite(Setting setting, int value) {
        JsonObject json = encode(get());
        json.addProperty(setting.key(), value);
        return write(file, parse(file, json));
    }

    public int effectiveRegionThreads() {
        return effective(regionThreads, Runtime.getRuntime().availableProcessors());
    }

    public int effectiveChunkThreads() {
        return effective(chunkThreads, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    private static int effective(int threads, int allCores) {
        return threads > 0 ? threads : allCores;
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
