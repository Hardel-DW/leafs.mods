package fr.hardel.leafs.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.LongFunction;

/**
 * Position-keyed resolution to the owning unit's world payload. Attached-constant until
 * {@link #route} is called at activation; afterwards a position with no owning region still resolves
 * to the attached payload, whose structures the level-serial remainder keeps draining.
 */
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
