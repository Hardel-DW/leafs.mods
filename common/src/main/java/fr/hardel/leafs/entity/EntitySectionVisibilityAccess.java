package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.minecraft.world.level.entity.Visibility;

public interface EntitySectionVisibilityAccess {

    void leafs$bindInitialVisibility(Long2ObjectFunction<Visibility> visibility);
}
