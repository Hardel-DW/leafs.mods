package fr.hardel.leafs.entity;

import net.minecraft.server.level.ServerLevel;

/** Implemented onto the level-blind entity indexes by mixin: the borrowing server thread needs the level to find a region. */
public interface LevelBoundAccess {

    void leafs$bindLevel(ServerLevel level);
}
