package fr.hardel.leafs.world;

/** Implemented onto {@code LevelChunk} by mixin: what the chunk ticks beyond vanilla's own containers. */
public interface ChunkTickAccess {

    ChunkTickers leafs$tickers();

    ChunkBlockEvents leafs$blockEvents();
}
