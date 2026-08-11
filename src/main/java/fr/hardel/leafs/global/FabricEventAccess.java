package fr.hardel.leafs.global;

/** Implemented onto fabric-api-base's {@code ArrayBackedEvent} by mixin: the subscriber probe the public Event API lacks. */
public interface FabricEventAccess {

    boolean leafs$hasSubscribers();
}
