package fr.hardel.leafs.world;

/** Implemented onto {@code ServerLevel} by mixin. */
public interface ServerLevelWorldAccess {

    RegionWorldData leafs$worldData();

    WorldDataRouter leafs$worldRouter();
}
