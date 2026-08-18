package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ServerLevel;

/** Implemented onto {@code SectionStorage} by mixin: the storage is built level-blind, the POI gate needs its level. */
public interface SectionStorageAccess {

    void leafs$bindLevel(ServerLevel level);

    ServerLevel leafs$level();
}
