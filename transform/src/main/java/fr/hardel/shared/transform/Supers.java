package fr.hardel.shared.transform;

/** The super class of a class, read from its bytecode without loading it, or null when it is unknown. */
@FunctionalInterface
public interface Supers {

    String superName(String internalName);
}
