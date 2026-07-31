package fr.hardel.leafs.chunk;

/** Implemented onto {@code ServerChunkCache} by mixin. */
public interface ChunkThreadAccess {

    ChunkSystemThread leafs$chunkThread();
}
