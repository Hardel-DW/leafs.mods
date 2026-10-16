package fr.hardel.shared;

/** Called by the bytecode the transformer writes right after a collection of a mod is built for a shared field. */
public final class Shared {

    private Shared() {
    }

    public static Object wrap(Object collection) {
        return collection == null ? null : Facade.of(collection.getClass()).wrap(collection);
    }
}
