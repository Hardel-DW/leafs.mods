package fr.hardel.leafs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPropertiesTest {

    @Test
    void aMissingFileIsCreatedWithTheKeyOff(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("server.properties");

        ServerProperties.disableSyncChunkWrites(file);

        assertEquals(List.of("sync-chunk-writes=false"), Files.readAllLines(file));
    }

    @Test
    void anExistingFileKeepsItsOtherLinesAndLosesTheOldValue(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("server.properties");
        Files.write(file, List.of("#Minecraft server properties", "sync-chunk-writes=true", "max-players=20"));

        ServerProperties.disableSyncChunkWrites(file);

        assertEquals(List.of("#Minecraft server properties", "max-players=20", "sync-chunk-writes=false"), Files.readAllLines(file));
    }
}
