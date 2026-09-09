package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Level-wide work anchored to a position, raids and the dragon fight: the owner of the position's chunk ticks it, a region for its chunks and ring, the server thread where no region is. */
public final class AnchoredTickers {
    private final List<AnchoredTicker> anchors = new CopyOnWriteArrayList<>();

    public void add(AnchoredTicker ticker) {
        anchors.add(ticker);
    }

    public void tick(Region<?> region) {
        for (AnchoredTicker anchor : live()) {
            long chunkKey = anchor.chunkKey();
            if (region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey))) {
                anchor.body().run();
            }
        }
    }

    /** The serial pass takes the chunk for the tick, like a command; a region born meanwhile is held by that borrow and ticks the anchor itself. */
    public void tickUnowned(LevelRegions regions) {
        for (AnchoredTicker anchor : live()) {
            long chunkKey = anchor.chunkKey();
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            if (regions.regionizer().regionAt(chunkX, chunkZ) != null) {
                continue;
            }

            RegionBorrow.hold(borrow -> {
                borrow.borrow(regions, chunkX, chunkZ);
                if (regions.regionizer().regionAt(chunkX, chunkZ) == null) {
                    anchor.body().run();
                }

                return null;
            });
        }
    }

    private List<AnchoredTicker> live() {
        anchors.removeIf(anchor -> anchor.finished().getAsBoolean());
        return anchors;
    }
}
