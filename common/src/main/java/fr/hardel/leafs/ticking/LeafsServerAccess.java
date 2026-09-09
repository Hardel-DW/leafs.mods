package fr.hardel.leafs.ticking;

/** Implemented onto {@code MinecraftServer} by mixin: one {@link TickingManager} per server instance. */
public interface LeafsServerAccess {

    TickingManager leafs$ticking();
}
