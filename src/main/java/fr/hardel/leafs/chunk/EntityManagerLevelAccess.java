package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ServerLevel;

/** Implemented onto {@code PersistentEntitySectionManager} by mixin: binds its owning level. */
public interface EntityManagerLevelAccess {

    void leafs$bindLevel(ServerLevel level);
}
