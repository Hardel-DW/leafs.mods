package fr.hardel.shared.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

public final class SharedTransform {
    private static final int CONSTANT_CLASS = 7;
    private static final List<String> PLATFORM = List.of("java/", "javax/", "jdk/", "sun/", "com/sun/", "net/minecraft/", "com/mojang/", "net/fabricmc/", "net/neoforged/",
        "org/spongepowered/", "com/llamalad7/", "org/objectweb/", "fr/hardel/", "it/unimi/dsi/fastutil/", "com/google/");

    private SharedTransform() {
    }

    public static void load() {
        List.of(SharedFields.class, Births.class, Supers.class, CollectionTypes.class);
    }

    public static boolean covers(String internalName) {
        return PLATFORM.stream().noneMatch(internalName::startsWith);
    }

    /** Reads only the constant pool: a class that names no collection type builds none. */
    public static boolean mayRewrite(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        char[] buffer = new char[reader.getMaxStringLength()];
        for (int item = 1; item < reader.getItemCount(); item++) {
            int offset = reader.getItem(item);
            if (offset > 0 && classBytes[offset - 1] == CONSTANT_CLASS && CollectionTypes.isCollection(reader.readUTF8(offset, buffer))) {
                return true;
            }
        }

        return false;
    }

    public static boolean wouldRewrite(byte[] classBytes, Supers supers) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return apply(node, supers);
    }

    public static boolean apply(ClassNode node, Supers supers) {
        Set<String> shared = SharedFields.of(node, supers);
        return !shared.isEmpty() && Births.rewrite(node, shared);
    }
}
