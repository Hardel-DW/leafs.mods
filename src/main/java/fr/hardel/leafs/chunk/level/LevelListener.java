package fr.hardel.leafs.chunk.level;

/** Told each settled level, then once when the batch is out. */
public interface LevelListener {
    void changed(long chunkKey, int oldLevel, int newLevel);

    default void published() {
    }
}
