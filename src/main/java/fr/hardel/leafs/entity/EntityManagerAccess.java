package fr.hardel.leafs.entity;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.Visibility;

/** The persistence internals of {@code PersistentEntitySectionManager} that the region pipeline drives. */
public interface EntityManagerAccess {

    void leafs$bindPersistence(RegionEntityPersistence persistence);

    Visibility leafs$visibility(long chunkKey);

    boolean leafs$unloadChunk(long chunkKey);

    boolean leafs$storeChunk(long chunkKey);

    LongSet leafs$chunksToUnload();
}
