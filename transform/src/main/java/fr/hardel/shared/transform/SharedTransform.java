package fr.hardel.shared.transform;

import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

public final class SharedTransform {
    private static final List<String> PLATFORM = List.of("java/", "javax/", "jdk/", "sun/", "com/sun/", "net/minecraft/", "com/mojang/", "net/fabricmc/", "net/neoforged/",
        "org/spongepowered/", "com/llamalad7/", "org/objectweb/", "fr/hardel/", "it/unimi/dsi/fastutil/", "com/google/");

    private SharedTransform() {
    }

    public static void load() {
        List.of(SharedFields.class, Births.class, Supers.class);
    }

    public static boolean covers(String internalName) {
        return PLATFORM.stream().noneMatch(internalName::startsWith);
    }

    public static boolean apply(ClassNode node, Supers supers) {
        Set<String> shared = SharedFields.of(node, supers);
        return !shared.isEmpty() && Births.rewrite(node, shared);
    }
}
