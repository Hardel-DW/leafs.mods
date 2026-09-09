package fr.hardel.leafs.chunk;

import fr.hardel.leafs.chunk.disk.ChunkWrites;

/** The disk thread of a level's chunk storage knows the writes on their way to it. */
public interface ChunkWritesAccess {
    void leafs$bind(ChunkWrites writes);
}
