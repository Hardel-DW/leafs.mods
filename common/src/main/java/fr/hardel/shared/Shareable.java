package fr.hardel.shared;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

/** A plain mutable collection of java.util or fastutil, which a subclass can lock without changing its contract. */
final class Shareable {

    private Shareable() {
    }

    static @Nullable Constructor<?> copyConstructor(Class<?> type) {
        if (!shareable(type)) {
            return null;
        }

        return Arrays.stream(type.getConstructors())
            .filter(constructor -> constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].isAssignableFrom(type))
            .reduce((first, second) -> first.getParameterTypes()[0].isAssignableFrom(second.getParameterTypes()[0]) ? second : first)
            .orElse(null);
    }

    private static boolean shareable(Class<?> type) {
        int modifiers = type.getModifiers();
        return Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isAbstract(modifiers) && type.getEnclosingClass() == null
            && library(type.getPackageName()) && collection(type) && !alreadySafe(type);
    }

    private static boolean library(String packageName) {
        return packageName.equals("java.util") || packageName.startsWith("it.unimi.dsi.fastutil.");
    }

    private static boolean collection(Class<?> type) {
        return Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type);
    }

    private static boolean alreadySafe(Class<?> type) {
        return SharedCollection.class.isAssignableFrom(type) || Vector.class.isAssignableFrom(type) || Hashtable.class.isAssignableFrom(type);
    }
}
