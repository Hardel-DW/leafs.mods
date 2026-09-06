package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongPredicate;

/** Level-wide work anchored to a position, raids and the dragon fight: the region owning the position ticks it, and it outlives its chunk. */
public final class AnchoredTickers {
    private final List<AnchoredTicker> anchors = new CopyOnWriteArrayList<>();

    public void add(AnchoredTicker ticker) {
        anchors.add(ticker);
    }

    public void tick(Region<?> region, LongPredicate tickingChunk) {
        for (AnchoredTicker anchor : anchors) {
            if (anchor.finished().getAsBoolean()) {
                anchors.remove(anchor);
                continue;
            }

            long chunkKey = anchor.chunkKey();
            if (region.owns(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) && tickingChunk.test(chunkKey)) {
                anchor.body().run();
            }
        }
    }
}
