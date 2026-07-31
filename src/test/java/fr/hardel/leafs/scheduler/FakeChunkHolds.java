package fr.hardel.leafs.scheduler;

import fr.hardel.leafs.region.CoordinateKey;
import fr.hardel.leafs.region.Regionizer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Stands in for chunk/'s tickets: acquiring an unloaded chunk loads it into the regionizer. */
final class FakeChunkHolds implements ChunkHoldController {
    private final Regionizer<TestRegionData> regionizer;
    private final LongOpenHashSet worldLoaded = new LongOpenHashSet();
    private final LongOpenHashSet holdLoaded = new LongOpenHashSet();
    int acquireCalls;
    int releaseCalls;

    FakeChunkHolds(Regionizer<TestRegionData> regionizer) {
        this.regionizer = regionizer;
    }

    void loadChunk(int chunkX, int chunkZ) {
        worldLoaded.add(CoordinateKey.pack(chunkX, chunkZ));
        regionizer.addChunk(chunkX, chunkZ);
    }

    void unloadChunk(int chunkX, int chunkZ) {
        worldLoaded.remove(CoordinateKey.pack(chunkX, chunkZ));
        regionizer.removeChunk(chunkX, chunkZ);
    }

    @Override
    public void acquire(int chunkX, int chunkZ) {
        acquireCalls++;
        long key = CoordinateKey.pack(chunkX, chunkZ);
        if (!worldLoaded.contains(key) && holdLoaded.add(key)) {
            regionizer.addChunk(chunkX, chunkZ);
        }
    }

    @Override
    public void release(int chunkX, int chunkZ) {
        releaseCalls++;
        long key = CoordinateKey.pack(chunkX, chunkZ);
        if (holdLoaded.remove(key)) {
            regionizer.removeChunk(chunkX, chunkZ);
        }
    }

    boolean hasActiveHolds() {
        return !holdLoaded.isEmpty();
    }
}
