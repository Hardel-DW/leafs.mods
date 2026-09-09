package fr.hardel.leafs.ticking;

import fr.hardel.leafs.world.AnchoredTickers;

/** Implemented onto {@code ServerLevel} by mixin. */
public interface ServerLevelRegionAccess {

    LevelRegions leafs$regions();

    AnchoredTickers leafs$anchors();
}
