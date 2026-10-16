package fr.hardel.shared.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fields every game thread can reach: the static fields, and the fields of an object the whole world shares, a block, an item, saved data,
 * an enum constant or a class holding its own instance. A block entity or an entity belongs to one region, its fields are never shared.
 */
final class SharedFields {
    private static final Set<String> SHARED_BASES = Set.of("net/minecraft/world/level/block/Block", "net/minecraft/world/item/Item", "net/minecraft/world/level/saveddata/SavedData");
    private static final String OBJECT = "java/lang/Object";

    private SharedFields() {
    }

    static Set<String> of(ClassNode node, Supers supers) {
        boolean sharedInstance = sharedObject(node, supers);
        return node.fields.stream()
            .filter(field -> field.desc.startsWith("L") && (isStatic(field) || sharedInstance))
            .map(field -> key(field.name, field.desc))
            .collect(Collectors.toSet());
    }

    static String key(String name, String descriptor) {
        return "%s:%s".formatted(name, descriptor);
    }

    private static boolean sharedObject(ClassNode node, Supers supers) {
        return (node.access & Opcodes.ACC_ENUM) != 0 || holdsItself(node) || extendsSharedBase(node.superName, supers);
    }

    private static boolean holdsItself(ClassNode node) {
        String self = "L%s;".formatted(node.name);
        return node.fields.stream().anyMatch(field -> isStatic(field) && field.desc.equals(self));
    }

    private static boolean extendsSharedBase(String superName, Supers supers) {
        for (String current = superName; current != null && !current.equals(OBJECT); current = supers.superName(current)) {
            if (SHARED_BASES.contains(current)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isStatic(FieldNode field) {
        return (field.access & Opcodes.ACC_STATIC) != 0;
    }
}
