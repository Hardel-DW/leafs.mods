package fr.hardel.leafs.chunk.level;

public interface LevelListener {
    void changed(long chunkKey, int oldLevel, int newLevel);

    default void published() {
    }

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
