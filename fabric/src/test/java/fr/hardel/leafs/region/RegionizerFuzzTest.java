package fr.hardel.leafs.region;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Seeded random churn with the invariant check applied throughout; shift 2 so the topology changes often. */
class RegionizerFuzzTest {
    private static final int OPERATIONS = 4000;
    private static final int CHECK_INTERVAL = 50;
    private static final int COORDINATE_RANGE = 128;
    private static final int CLUSTER_SPREAD = 8;

    @ParameterizedTest
    @ValueSource(longs = {1, 7, 42, 1337, 20260731})
    void randomChurnPreservesEveryInvariant(long seed) {
        Random random = new Random(seed);
        Regionizer<Object> regionizer = new Regionizer<>(2, 1, 1, new RecordingCallbacks());
        LongOpenHashSet chunks = new LongOpenHashSet();
        LongArrayList chunkList = new LongArrayList();
        List<Region<Object>> ticking = new ArrayList<>();

        for (int operation = 0; operation < OPERATIONS; operation++) {
            int roll = random.nextInt(100);
            if (roll < 55 || chunks.isEmpty()) {
                addRandomChunk(random, regionizer, chunks, chunkList);
            } else if (roll < 85) {
                removeRandomChunk(random, regionizer, chunks, chunkList);
            } else if (roll < 93) {
                markRandomRegionTicking(random, regionizer, ticking);
            } else {
                releaseRandomTickingRegion(random, ticking);
            }

            if (operation % CHECK_INTERVAL == 0) {
                RegionizerAssertions.assertInvariants(regionizer, false);
            }
        }

        while (!ticking.isEmpty()) {
            releaseRandomTickingRegion(random, ticking);
        }

        RegionizerAssertions.assertInvariants(regionizer, true);
        for (LongIterator iterator = chunks.iterator(); iterator.hasNext(); ) {
            long chunkKey = iterator.nextLong();
            int chunkX = CoordinateKey.x(chunkKey);
            int chunkZ = CoordinateKey.z(chunkKey);
            Region<Object> owner = regionizer.regionAt(chunkX, chunkZ);
            assertNotNull(owner, "chunk [" + chunkX + ", " + chunkZ + "] lost its region");
            assertSame(owner, regionizer.regionAtUnsynchronised(chunkX, chunkZ));
        }
    }

    private void addRandomChunk(Random random, Regionizer<Object> regionizer, LongOpenHashSet chunks, LongArrayList chunkList) {
        int chunkX;
        int chunkZ;
        if (!chunkList.isEmpty() && random.nextBoolean()) {
            long anchor = chunkList.getLong(random.nextInt(chunkList.size()));
            chunkX = CoordinateKey.x(anchor) + random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
            chunkZ = CoordinateKey.z(anchor) + random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
        } else {
            chunkX = random.nextInt(COORDINATE_RANGE * 2 + 1) - COORDINATE_RANGE;
            chunkZ = random.nextInt(COORDINATE_RANGE * 2 + 1) - COORDINATE_RANGE;
        }

        long chunkKey = CoordinateKey.pack(chunkX, chunkZ);
        if (!chunks.add(chunkKey)) {
            return;
        }

        chunkList.add(chunkKey);
        regionizer.addChunk(chunkX, chunkZ);
    }

    private void removeRandomChunk(Random random, Regionizer<Object> regionizer, LongOpenHashSet chunks, LongArrayList chunkList) {
        int index = random.nextInt(chunkList.size());
        long chunkKey = chunkList.getLong(index);
        chunkList.set(index, chunkList.getLong(chunkList.size() - 1));
        chunkList.removeLong(chunkList.size() - 1);
        chunks.remove(chunkKey);
        regionizer.removeChunk(CoordinateKey.x(chunkKey), CoordinateKey.z(chunkKey));
    }

    private void markRandomRegionTicking(Random random, Regionizer<Object> regionizer, List<Region<Object>> ticking) {
        List<Region<Object>> regions = new ArrayList<>(regionizer.regionsView());
        if (regions.isEmpty()) {
            return;
        }

        Region<Object> candidate = regions.get(random.nextInt(regions.size()));
        if (candidate.tryMarkTicking()) {
            ticking.add(candidate);
        }
    }

    private void releaseRandomTickingRegion(Random random, List<Region<Object>> ticking) {
        if (ticking.isEmpty()) {
            return;
        }

        int index = random.nextInt(ticking.size());
        Region<Object> region = ticking.get(index);
        ticking.set(index, ticking.get(ticking.size() - 1));
        ticking.remove(ticking.size() - 1);
        region.markNotTicking();
    }
}
