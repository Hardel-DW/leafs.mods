package fr.hardel.leafs.world;

import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionCallbacks;
import fr.hardel.leafs.region.Regionizer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An anchor follows its position: a raid whose centre moves ticks on the owner of the new centre. */
class AnchoredTickersTest {

    private static final RegionCallbacks<Object> NO_CALLBACKS = new RegionCallbacks<>() {
        @Override
        public Object createData(Region<Object> region) {
            return new Object();
        }

        @Override
        public void onRegionCreate(Region<Object> region) {
        }

        @Override
        public void onRegionDestroy(Region<Object> region) {
        }

        @Override
        public void onRegionActive(Region<Object> region) {
        }

        @Override
        public void onRegionInactive(Region<Object> region) {
        }

        @Override
        public void merge(Region<Object> from, Region<Object> into, LongList movedChunks) {
        }

        @Override
        public void split(Region<Object> parent, Long2ObjectMap<Region<Object>> sectionToChild, List<Region<Object>> children) {
        }
    };

    @Test
    void anAnchorTicksWhereItsPositionIsNow() {
        Regionizer<Object> regionizer = new Regionizer<>(1, 1, 1, NO_CALLBACKS);
        regionizer.addChunk(0, 0);
        Region<Object> region = regionizer.regionAt(0, 0);
        AtomicReference<BlockPos> centre = new AtomicReference<>(new BlockPos(500, 64, 500));
        AtomicInteger ticks = new AtomicInteger();
        AnchoredTickers anchors = new AnchoredTickers();
        anchors.add(new AnchoredTicker(centre::get, ticks::incrementAndGet, () -> false));

        anchors.tick(region);
        centre.set(new BlockPos(8, 64, 8));
        anchors.tick(region);

        assertEquals(1, ticks.get(), "far away first, then inside the region");
    }

    /** A region owns its ring without ticking it; an anchor there is still its work, the raid or the fight handles an unloaded chunk itself. */
    @Test
    void anAnchorInTheRingTicksOnItsRegion() {
        Regionizer<Object> regionizer = new Regionizer<>(1, 1, 1, NO_CALLBACKS);
        regionizer.addChunk(0, 0);
        Region<Object> region = regionizer.regionAt(0, 0);
        AtomicInteger ticks = new AtomicInteger();
        AnchoredTickers anchors = new AnchoredTickers();
        anchors.add(new AnchoredTicker(() -> new BlockPos(40, 64, 40), ticks::incrementAndGet, () -> false));

        anchors.tick(region);

        assertEquals(region, regionizer.regionAt(2, 2), "chunk 2,2 is in the ring");
        assertEquals(1, ticks.get());
    }
}
