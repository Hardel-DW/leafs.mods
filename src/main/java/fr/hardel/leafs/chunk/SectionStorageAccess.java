package fr.hardel.leafs.chunk;

import net.minecraft.server.level.ServerLevel;

/** Implemented onto {@code SectionStorage} by mixin: the storage is built level-blind, the read gate needs its level; every subclass takes the same lock. */
public interface SectionStorageAccess {

    void leafs$bindLevel(ServerLevel level);

    ServerLevel leafs$level();

    SectionStorageLock leafs$lock();
}
