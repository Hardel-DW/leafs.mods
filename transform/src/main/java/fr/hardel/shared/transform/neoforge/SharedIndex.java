package fr.hardel.shared.transform.neoforge;

import fr.hardel.shared.transform.SharedTransform;
import net.neoforged.fml.jarcontents.JarResource;
import net.neoforged.neoforgespi.locating.IModFile;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The classes the transform rewrites, found once before the game loads any: FML only reads in full the classes a processor claims.
 * The first pass reads the super class of every class and keeps the classes naming a collection, the second analyses only those.
 */
final class SharedIndex {
    private static final String CLASS_SUFFIX = ".class";

    private final Map<String, String> supers;
    private final Set<String> rewritten;

    private SharedIndex(Map<String, String> supers, Set<String> rewritten) {
        this.supers = supers;
        this.rewritten = rewritten;
    }

    static SharedIndex scan(Collection<IModFile> files) {
        Map<String, String> supers = new ConcurrentHashMap<>();
        Map<String, IModFile> candidates = new ConcurrentHashMap<>();
        files.parallelStream().forEach(file -> file.getContents().visitContent((path, resource) -> visit(file, path, resource, supers, candidates)));
        Set<String> rewritten = candidates.entrySet().parallelStream()
            .filter(candidate -> SharedTransform.wouldRewrite(classBytes(candidate.getValue(), candidate.getKey()), supers::get))
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
        return new SharedIndex(supers, rewritten);
    }

    boolean rewrites(String internalName) {
        return rewritten.contains(internalName);
    }

    String superName(String internalName) {
        return supers.get(internalName);
    }

    private static void visit(IModFile file, String path, JarResource resource, Map<String, String> supers, Map<String, IModFile> candidates) {
        if (!path.endsWith(CLASS_SUFFIX) || path.startsWith("META-INF/")) {
            return;
        }

        String name = path.substring(0, path.length() - CLASS_SUFFIX.length());
        byte[] bytes = read(resource, path);
        String superName = new ClassReader(bytes).getSuperName();
        if (superName != null) {
            supers.put(name, superName);
        }

        if (SharedTransform.covers(name) && SharedTransform.mayRewrite(bytes)) {
            candidates.put(name, file);
        }
    }

    private static byte[] classBytes(IModFile file, String name) {
        String path = "%s%s".formatted(name, CLASS_SUFFIX);
        try {
            return file.getContents().readFile(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Leafs could not read %s in %s".formatted(path, file.getFilePath()), failure);
        }
    }

    private static byte[] read(JarResource resource, String path) {
        try {
            return resource.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("Leafs could not read %s".formatted(path), failure);
        }
    }
}
