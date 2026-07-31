package fr.hardel.leafs.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeafsConfigTest {

    @Test
    void missingFileWritesDefaultsAndReturnsThem(@TempDir Path directory) {
        Path file = directory.resolve("leafs.json");

        LeafsConfig config = LeafsConfig.load(file);

        assertTrue(Files.exists(file));
        assertEquals(LeafsConfig.AUTO_THREADS, config.regionThreads());
        assertEquals(4, config.gridSectionShift());
        assertTrue(config.compatBarrier());

        LeafsConfig reloaded = LeafsConfig.load(file);
        assertEquals(config.regionThreads(), reloaded.regionThreads());
        assertEquals(config.gridSectionShift(), reloaded.gridSectionShift());
    }

    @Test
    void existingFileOverridesDefaults(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("leafs.json");
        Files.writeString(file, """
            {"regionThreads": 8, "compatBarrier": false}
            """);

        LeafsConfig config = LeafsConfig.load(file);

        assertEquals(8, config.regionThreads());
        assertEquals(8, config.effectiveRegionThreads());
        assertFalse(config.compatBarrier());
        assertEquals(4, config.gridSectionShift());
        assertTrue(config.perRegionLogs());
    }

    @Test
    void autoThreadsResolvesToAvailableProcessors() {
        assertEquals(Runtime.getRuntime().availableProcessors(), LeafsConfig.defaults().effectiveRegionThreads());
    }

    @Test
    void sectionChunkSizeDerivesFromShift() {
        assertEquals(16, LeafsConfig.defaults().sectionChunkSize());
    }

    @Test
    void invalidValuesFailTheBoot(@TempDir Path directory) throws IOException {
        Path malformed = directory.resolve("a.json");
        Files.writeString(malformed, "{oops");
        assertThrows(IllegalArgumentException.class, () -> LeafsConfig.load(malformed));

        Path wrongType = directory.resolve("b.json");
        Files.writeString(wrongType, "{\"regionThreads\": \"lots\"}");
        assertThrows(IllegalArgumentException.class, () -> LeafsConfig.load(wrongType));

        Path outOfRange = directory.resolve("c.json");
        Files.writeString(outOfRange, "{\"gridSectionShift\": 12}");
        assertThrows(IllegalArgumentException.class, () -> LeafsConfig.load(outOfRange));

        Path empty = directory.resolve("d.json");
        Files.writeString(empty, "");
        assertThrows(IllegalArgumentException.class, () -> LeafsConfig.load(empty));
    }
}
