package fr.hardel.leafs.entity;

import fr.hardel.excess.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.Visibility;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEntityPersistenceTest {
    private static final int SIDE = 7;
    private static final int CHUNKS = SIDE * SIDE;
    private static final int RUNAWAY_ATTEMPTS = CHUNKS * 4;

    private static final class FailingManager implements EntityManagerAccess {
        private final LongSet chunksToUnload = new ConcurrentLongSet();
        private int unloadAttempts;

        @Override
        public void leafs$bindPersistence(RegionEntityPersistence persistence) {
        }

        @Override
        public Visibility leafs$visibility(long chunkKey) {
            return Visibility.HIDDEN;
        }

        @Override
        public boolean leafs$unloadChunk(long chunkKey) {
            unloadAttempts++;
            if (unloadAttempts > RUNAWAY_ATTEMPTS) {
                throw new AssertionError("unloadHidden kept retrying the same chunk inside a single pass");
            }

            return false;
        }

        @Override
        public boolean leafs$storeChunk(long chunkKey) {
            return false;
        }

        @Override
        public LongSet leafs$chunksToUnload() {
            return chunksToUnload;
        }
    }

    @Test
    void aFailingUnloadIsHandedToTheOwnerAgainOnce() {
        FailingManager manager = new FailingManager();
        LongSet pending = manager.leafs$chunksToUnload();
        Deque<Runnable> owner = new ArrayDeque<>();
        RegionEntityPersistence persistence = new RegionEntityPersistence(null, manager, () -> {
        }, (chunkKey, task) -> owner.add(task));
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                pending.add(ChunkPos.pack(x, z));
                persistence.unloadHiddenLater(ChunkPos.pack(x, z));
            }
        }

        for (int index = 0; index < CHUNKS; index++) {
            owner.poll().run();
        }

        assertEquals(CHUNKS, manager.unloadAttempts, "one pass tries each chunk once");
        assertEquals(CHUNKS, owner.size(), "every failed chunk waits again on its owner, once");
        assertEquals(CHUNKS, pending.size(), "every chunk stays queued meanwhile");
        assertTrue(pending.contains(ChunkPos.pack(0, 0)), "the queue still holds the chunks themselves");
    }
}
