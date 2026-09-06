package fr.hardel.leafs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The server.properties of a dedicated server; a singleplayer world has none and gets nothing. */
public final class ServerProperties {
    private static final String SYNC_CHUNK_WRITES = "sync-chunk-writes";

    private ServerProperties() {
    }

    /** The first start of Leafs writes the key once, visible and editable by the admin; the file is created when vanilla has not written it yet. */
    public static void disableSyncChunkWrites(Path file) {
        List<String> lines = Files.exists(file) ? readLines(file) : new ArrayList<>();
        lines.removeIf(line -> line.startsWith(SYNC_CHUNK_WRITES + "="));
        lines.add(SYNC_CHUNK_WRITES + "=false");
        try {
            Files.write(file, lines);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write " + file, exception);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return new ArrayList<>(Files.readAllLines(file));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + file, exception);
        }
    }
}
