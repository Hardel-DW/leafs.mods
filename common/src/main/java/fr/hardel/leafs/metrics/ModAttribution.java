package fr.hardel.leafs.metrics;

import fr.hardel.leafs.Leafs;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ModAttribution {
    private static final Set<String> NOT_SUSPECTS = Set.of("minecraft", "java", "mixinextras", Leafs.MOD_ID);

    public record Suspect(String modId, String version, String frame) {}

    private final Function<String, Optional<Suspect>> ownerOfClass;
    private final Map<String, Optional<Suspect>> cache = new ConcurrentHashMap<>();

    public ModAttribution(Function<String, Optional<Suspect>> ownerOfClass) {
        this.ownerOfClass = ownerOfClass;
    }

    public static ModAttribution fromOrigins(Map<Path, Suspect> byOrigin) {
        return new ModAttribution(className -> ownerOf(className, byOrigin));
    }

    public Optional<Suspect> suspect(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        for (StackTraceElement frame : cause.getStackTrace()) {
            Optional<Suspect> owner = cache.computeIfAbsent(frame.getClassName(), ownerOfClass);
            if (owner.isPresent()) {
                return Optional.of(new Suspect(owner.get().modId(), owner.get().version(), frame.toString()));
            }
        }

        return Optional.empty();
    }

    private static Optional<Suspect> ownerOf(String className, Map<Path, Suspect> byOrigin) {
        Path source = codeSourceOf(className);
        Suspect mod = source == null ? null : byOrigin.get(source);
        return mod == null || NOT_SUSPECTS.contains(mod.modId()) ? Optional.empty() : Optional.of(mod);
    }

    private static Path codeSourceOf(String className) {
        try {
            CodeSource source = Class.forName(className, false, ModAttribution.class.getClassLoader()).getProtectionDomain().getCodeSource();
            return source == null ? null : Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (ClassNotFoundException | LinkageError | URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }
}
