package fr.hardel.leafs.chunk.level;

/** Told each settled level, then once when the batch is out. */
public interface LevelListener {
    void changed(long chunkKey, int oldLevel, int newLevel);

    default void published() {
    }

    /** Both hear every change, this one first. */
    default LevelListener and(LevelListener other) {
        LevelListener first = this;
        return new LevelListener() {
            @Override
            public void changed(long chunkKey, int oldLevel, int newLevel) {
                first.changed(chunkKey, oldLevel, newLevel);
                other.changed(chunkKey, oldLevel, newLevel);
            }

            @Override
            public void published() {
                first.published();
                other.published();
            }
        };
    }
}
