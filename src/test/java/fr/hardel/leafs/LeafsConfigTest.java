package fr.hardel.leafs;

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
        assertEquals(15, written.debug().watchdogWarnSeconds());
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
        assertEquals(Runtime.getRuntime().availableProcessors(), config.effectiveChunkThreads());
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
        assertEquals(Runtime.getRuntime().availableProcessors(), LeafsConfig.defaults().effectiveChunkThreads());
    }

    @Test
    void invalidFilesFailTheBootNamingFileAndCause(@TempDir Path directory) throws IOException {
        record Invalid(String json, String cause) {
        }

        List<Invalid> cases = List.of(
            new Invalid("{\"region_threads\": 0}", "region_threads"),
            new Invalid("{\"chunk_threads\": 0}", "chunk_threads"),
            new Invalid("{\"debug\": {\"watchdog_warn_seconds\": 0}}", null),
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
