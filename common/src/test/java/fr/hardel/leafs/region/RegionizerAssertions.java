package fr.hardel.leafs.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class RegionizerAssertions {

    private RegionizerAssertions() {
    }

    private static boolean hasPendingLinks(Region<?> region) {
        return !region.mergeIntoLater.isEmpty() || !region.expectingMergeFrom.isEmpty();
    }

    public static <R> void assertInvariants(Regionizer<R> regionizer, boolean strict) {
        var sections = regionizer.sections;
        int bufferRadius = regionizer.bufferRadius;
        int mergeRadius = regionizer.mergeRadius;

        for (var entry : sections.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            RegionSection<R> section = entry.getValue();
            int sectionX = CoordinateKey.x(key);
            int sectionZ = CoordinateKey.z(key);
            Region<R> owner = section.region();

            assertNotNull(owner, "section [%s, %s] has no owner".formatted(sectionX, sectionZ));
            assertTrue(owner.sectionKeys.contains(key), "owner of section [%s, %s] does not list it".formatted(sectionX, sectionZ));
            assertTrue(regionizer.regionsView().contains(owner), "owner of section [%s, %s] is not registered".formatted(sectionX, sectionZ));

            int expectedNonEmptyNeighbours = 0;
            for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
                for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }

                    RegionSection<R> neighbour = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                    if (neighbour != null && !neighbour.isEmpty()) {
                        expectedNonEmptyNeighbours++;
                    }
                    if (!section.isEmpty()) {
                        assertNotNull(neighbour, "buffer section [%s, %s] missing around non-empty section [%s, %s]".formatted(sectionX + dx, sectionZ + dz, sectionX, sectionZ));
                        if (neighbour.region() != owner) {
                            boolean linked = hasPendingLinks(owner) || hasPendingLinks(neighbour.region());
                            assertTrue(!strict && linked, "buffer section [%s, %s] not owned by the region of [%s, %s] and no merge is pending".formatted(
                                sectionX + dx, sectionZ + dz, sectionX, sectionZ));
                        }
                    }
                }
            }
            assertEquals(expectedNonEmptyNeighbours, section.nonEmptyNeighbours(), "wrong neighbour count for section [%s, %s]".formatted(sectionX, sectionZ));

            boolean isolated = section.isEmpty() && section.nonEmptyNeighbours() == 0;
            assertEquals(isolated, owner.deadSectionKeys.contains(key), "wrong dead mark for section [%s, %s]".formatted(sectionX, sectionZ));

            for (int dx = -mergeRadius; dx <= mergeRadius; dx++) {
                for (int dz = -mergeRadius; dz <= mergeRadius; dz++) {
                    RegionSection<R> neighbour = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                    if (neighbour == null || neighbour.region() == owner) {
                        continue;
                    }

                    Region<R> other = neighbour.region();
                    assertTrue(hasPendingLinks(owner) || hasPendingLinks(other), "regions #%s and #%s are within merge distance without a pending merge".formatted(
                        owner.id(), other.id()));
                }
            }
        }

        for (Region<R> region : regionizer.regionsView()) {
            assertTrue(region.state() != RegionState.DEAD, "dead %s still registered".formatted(region));
            for (Region<R> target : region.mergeIntoLater) {
                assertTrue(target.expectingMergeFrom.contains(region), "one-way merge link from %s to %s".formatted(region, target));
            }
            for (Region<R> source : region.expectingMergeFrom) {
                assertTrue(source.mergeIntoLater.contains(region), "one-way merge expectation on %s from %s".formatted(region, source));
            }
            if (strict) {
                assertTrue(region.mergeIntoLater.isEmpty() && region.expectingMergeFrom.isEmpty(), "%s still has pending merges in steady state".formatted(region));
                assertFalse(region.state() == RegionState.TRANSIENT, "%s is transient in steady state".formatted(region));
            }
        }
    }
}
