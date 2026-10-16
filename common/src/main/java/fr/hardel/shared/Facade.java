package fr.hardel.shared;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;

final class Facade {
    private static final Facade KEPT = new Facade(null);
    private static final ClassValue<Facade> FACADES = new ClassValue<>() {
        @Override
        protected Facade computeValue(Class<?> type) {
            Constructor<?> copy = Shareable.copyConstructor(type);
            return copy == null ? KEPT : new Facade(FacadeWriter.define(type, copy));
        }
    };

    private final @Nullable MethodHandle constructor;

    private Facade(@Nullable MethodHandle constructor) {
        this.constructor = constructor;
    }

    static Facade of(Class<?> type) {
        return FACADES.get(type);
    }

    Object wrap(Object collection) {
        return constructor == null ? collection : construct(constructor, collection);
    }

    private static Object construct(MethodHandle constructor, Object collection) {
        try {
            return (Object) constructor.invokeExact(collection);
        } catch (Throwable failure) {
            throw new IllegalStateException("Leafs could not share a %s".formatted(collection.getClass().getName()), failure);
        }
    }
}
