package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.loader.PlayerChunkLoader;

/** Implemented onto {@code ChunkMap} by mixin: the level's per-player chunk loader. */
public interface PlayerLoaderAccess {

    PlayerChunkLoader leafs$playerLoader();
}
