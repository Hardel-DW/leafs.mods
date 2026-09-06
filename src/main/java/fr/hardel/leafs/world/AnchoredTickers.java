package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import net.minecraft.core.SectionPos;

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

            int chunkX = SectionPos.blockToSectionCoord(anchor.pos().getX());
            int chunkZ = SectionPos.blockToSectionCoord(anchor.pos().getZ());
            if (region.owns(chunkX, chunkZ) && tickingChunk.test(anchor.chunkKey())) {
                anchor.body().run();

            }
        }
    }
}
