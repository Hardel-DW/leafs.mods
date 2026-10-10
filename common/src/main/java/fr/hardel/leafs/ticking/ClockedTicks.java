package fr.hardel.leafs.ticking;

import org.jspecify.annotations.Nullable;

/** A chunk tick container written in the time of a region, or in the level game time when it has none. */
public interface ClockedTicks {

    @Nullable RegionTime leafs$clock();

    void leafs$clock(@Nullable RegionTime clock);
}
