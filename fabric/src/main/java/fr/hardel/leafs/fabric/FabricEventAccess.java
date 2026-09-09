package fr.hardel.leafs.fabric;

/** Implemented onto fabric-api-base's {@code ArrayBackedEvent} by mixin: the subscriber probe the public Event API lacks. */
public interface FabricEventAccess {

    boolean leafs$hasSubscribers();
}
