package fr.hardel.leafs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record ServerProperties(Path file) {

    public void set(String key, String value) {
        try {
            List<String> lines = Files.notExists(file) ? new ArrayList<>() : new ArrayList<>(Files.readAllLines(file));
            lines.removeIf(line -> line.startsWith(key + "="));
            lines.add(key + "=" + value);
            Files.write(file, lines);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to update " + file, exception);
        }
    }
}
