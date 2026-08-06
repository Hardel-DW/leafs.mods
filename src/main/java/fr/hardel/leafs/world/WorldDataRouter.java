package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.LongFunction;

/** Unresolved positions fall back to the attached payload, which the level-serial remainder drains. */
public final class WorldDataRouter {
    private final RegionWorldData attached;
    private volatile LongFunction<RegionWorldData> resolver;

    public WorldDataRouter(RegionWorldData attached) {
        this.attached = attached;
        this.resolver = chunkKey -> attached;
    }

    public RegionWorldData attached() {
        return attached;
    }

    public void route(LongFunction<RegionWorldData> resolver) {
        this.resolver = resolver;
    }

    public RegionWorldData at(long chunkKey) {
        return resolver.apply(chunkKey);
    }

    public RegionWorldData at(BlockPos pos) {
        return resolver.apply(ChunkPos.pack(pos));
    }

    public RegionWorldData atChunk(int chunkX, int chunkZ) {
        return resolver.apply(ChunkPos.pack(chunkX, chunkZ));
    }
}
