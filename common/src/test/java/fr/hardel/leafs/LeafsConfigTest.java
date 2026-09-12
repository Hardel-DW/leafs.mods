package fr.hardel.leafs;

import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeafsConfigTest {

    @Test
    void missingFileWritesDefaultsThatReloadIdentically(@TempDir Path directory) {
        Path file = directory.resolve("leafs.json");

        LeafsConfig written = LeafsConfig.load(file);

        assertTrue(Files.exists(file));
        assertEquals(written, LeafsConfig.load(file));
        assertEquals(LeafsConfig.defaults(), written);
        assertEquals(LeafsConfig.Debug.DISABLED, written.debug().watchdogWarnSeconds());
        assertEquals(LeafsConfig.Debug.DISABLED, written.debug().slowTaskWarnMillis());
        assertEquals(LeafsConfig.MobCapScope.LEVEL, written.gameplay().mobCapScope());
        assertEquals(7, written.gameplay().mobCap().size());
        assertEquals(70, written.gameplay().mobCap(MobCategory.MONSTER));
    }

    /** The file is the persisted config: a second change starts from it, not from the config the server booted with. */
    @Test
    void aSecondRewriteKeepsTheFirstChange(@TempDir Path directory) {
        Path file = directory.resolve("leafs.json");
        LeafsConfig.load(file);
        LeafsConfig.rewrite(file, LeafsConfig.Setting.SECTION_SIZE, 4);
        LeafsConfig.rewrite(file, LeafsConfig.Setting.REGION_MERGE_DISTANCE, 3);
        LeafsConfig written = LeafsConfig.load(file);
        assertEquals(4, written.sectionSize());
        assertEquals(3, written.regionMergeDistance());
    }

    @Test
    void gameplayOverridesOneCapAndKeepsVanillaForTheRest(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("leafs.json");
        Files.writeString(file, """
            {"gameplay": {"mob_cap_scope": "region", "mob_cap": {"monster": 35}}}
            """);

        LeafsConfig.Gameplay gameplay = LeafsConfig.load(file).gameplay();

        assertEquals(LeafsConfig.MobCapScope.REGION, gameplay.mobCapScope());
        assertEquals(35, gameplay.mobCap(MobCategory.MONSTER));
        assertEquals(10, gameplay.mobCap(MobCategory.CREATURE));
    }

    @Test
    void partialFileOverridesItsKeysAndKeepsTheRest(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("leafs.json");
        Files.writeString(file, """
            {"region_threads": 8, "section_size": 32, "debug": {"per_region_logs": true}}
            """);

        LeafsConfig config = LeafsConfig.load(file);
        LeafsConfig defaults = LeafsConfig.defaults();

        assertEquals(8, config.effectiveRegionThreads());
        assertEquals(Runtime.getRuntime().availableProcessors() / 2, config.effectiveChunkThreads());
        assertEquals(32, config.sectionSize());
        assertEquals(5, config.sectionShift());
        assertTrue(config.debug().perRegionLogs());
        assertEquals(defaults.regionMergeDistance(), config.regionMergeDistance());
        assertEquals(defaults.debug().watchdogWarnSeconds(), config.debug().watchdogWarnSeconds());
    }

    @Test
    void absentThreadsResolveToEveryProcessor() {
        assertEquals(LeafsConfig.ALL_CORES, LeafsConfig.defaults().regionThreads());
        assertEquals(LeafsConfig.ALL_CORES, LeafsConfig.defaults().chunkThreads());
        assertEquals(Runtime.getRuntime().availableProcessors(), LeafsConfig.defaults().effectiveRegionThreads());
        assertEquals(Runtime.getRuntime().availableProcessors() / 2, LeafsConfig.defaults().effectiveChunkThreads());
    }

    /** A negative threshold is one the consumers never reach; the log stays off without a branch on their side. */
    @Test
    void negativeThresholdsAreNeverReached(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("leafs.json");
        Files.writeString(file, "{\"region_threads\": -3, \"debug\": {\"watchdog_warn_seconds\": -5, \"slow_task_warn_millis\": -1}}");

        LeafsConfig config = LeafsConfig.load(file);
        LeafsConfig.Debug off = config.debug();

        assertEquals(Runtime.getRuntime().availableProcessors(), config.effectiveRegionThreads());
        assertEquals(Long.MAX_VALUE, off.watchdogWarnNanos());
        assertEquals(Long.MAX_VALUE, off.slowTaskNanos());
        assertEquals(15_000_000_000L, new LeafsConfig.Debug(15, false, 50).watchdogWarnNanos());
        assertEquals(50_000_000L, new LeafsConfig.Debug(15, false, 50).slowTaskNanos());
    }

    @Test
    void invalidFilesFailTheBootNamingFileAndCause(@TempDir Path directory) throws IOException {
        record Invalid(String json, String cause) {
        }

        List<Invalid> cases = List.of(
            new Invalid("{\"region_threads\": 0}", "region_threads"),
            new Invalid("{\"chunk_threads\": 0}", "chunk_threads"),
            new Invalid("{\"debug\": {\"watchdog_warn_seconds\": 0}}", null),
            new Invalid("{\"debug\": {\"watchdog_warn_seconds\": 601}}", "watchdog_warn_seconds"),
            new Invalid("{\"debug\": {\"slow_task_warn_millis\": 60001}}", "slow_task_warn_millis"),
            new Invalid("{\"section_size\": 20}", "section_size"),
            new Invalid("{\"section_size\": 512}", null),
            new Invalid("{\"region_threads\": \"lots\"}", null),
            new Invalid("{oops", null),
            new Invalid("", null)
        );

        for (Invalid invalid : cases) {
            Path file = directory.resolve("leafs.json");
            Files.writeString(file, invalid.json());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> LeafsConfig.load(file), invalid.json());
            assertTrue(exception.getMessage().contains(file.toString()), invalid.json());
            if (invalid.cause() != null) {
                assertTrue(exception.getMessage().contains(invalid.cause()), invalid.json());
            }
        }
    }
}
