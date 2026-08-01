package fr.hardel.leafs.region;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural invariant checks shared by the scenario tests, the fuzz test and the region-feed test. */
public final class RegionizerAssertions {

    private RegionizerAssertions() {
    }

    private static boolean hasPendingLinks(Region<?> region) {
        return !region.mergeIntoLater.isEmpty() || !region.expectingMergeFrom.isEmpty();
    }

    /**
     * Verifies every structural invariant of the regionizer. {@code strict} additionally requires that
     * no merge is pending anywhere — the steady state once every region stopped ticking.
     */
    public static <R> void assertInvariants(Regionizer<R> regionizer, boolean strict) {
        Map<Long, RegionSection<R>> sections = regionizer.sectionsView();
        int bufferRadius = regionizer.bufferRadiusValue();
        int mergeRadius = regionizer.mergeRadiusValue();

        for (Map.Entry<Long, RegionSection<R>> entry : sections.entrySet()) {
            long key = entry.getKey();
            RegionSection<R> section = entry.getValue();
            int sectionX = CoordinateKey.x(key);
            int sectionZ = CoordinateKey.z(key);
            Region<R> owner = section.region();

            assertNotNull(owner, "section [" + sectionX + ", " + sectionZ + "] has no owner");
            assertTrue(owner.sectionKeys.contains(key), "owner of section [" + sectionX + ", " + sectionZ + "] does not list it");
            assertTrue(regionizer.regionsView().contains(owner), "owner of section [" + sectionX + ", " + sectionZ + "] is not registered");

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
                        assertNotNull(neighbour, "buffer section [" + (sectionX + dx) + ", " + (sectionZ + dz) + "] missing around non-empty section [" + sectionX + ", " + sectionZ + "]");
                        if (neighbour.region() != owner) {
                            boolean linked = hasPendingLinks(owner) || hasPendingLinks(neighbour.region());
                            assertTrue(!strict && linked, "buffer section [" + (sectionX + dx) + ", " + (sectionZ + dz) + "] not owned by the region of [" + sectionX + ", " + sectionZ + "] and no merge is pending");
                        }
                    }
                }
            }
            assertEquals(expectedNonEmptyNeighbours, section.nonEmptyNeighbours(), "wrong neighbour count for section [" + sectionX + ", " + sectionZ + "]");

            boolean isolated = section.isEmpty() && section.nonEmptyNeighbours() == 0;
            assertEquals(isolated, owner.deadSectionKeys.contains(key), "wrong dead mark for section [" + sectionX + ", " + sectionZ + "]");

            for (int dx = -mergeRadius; dx <= mergeRadius; dx++) {
                for (int dz = -mergeRadius; dz <= mergeRadius; dz++) {
                    RegionSection<R> neighbour = sections.get(CoordinateKey.pack(sectionX + dx, sectionZ + dz));
                    if (neighbour == null || neighbour.region() == owner) {
                        continue;
                    }

                    Region<R> other = neighbour.region();
                    assertTrue(hasPendingLinks(owner) || hasPendingLinks(other), "regions #" + owner.id() + " and #" + other.id() + " are within merge distance without a pending merge");
                }
            }
        }

        for (Region<R> region : regionizer.regionsView()) {
            assertTrue(region.state() != RegionState.DEAD, "dead " + region + " still registered");
            for (Region<R> target : region.mergeIntoLater) {
                assertTrue(target.expectingMergeFrom.contains(region), "one-way merge link from " + region + " to " + target);
            }
            for (Region<R> source : region.expectingMergeFrom) {
                assertTrue(source.mergeIntoLater.contains(region), "one-way merge expectation on " + region + " from " + source);
            }
            if (strict) {
                assertTrue(region.mergeIntoLater.isEmpty() && region.expectingMergeFrom.isEmpty(), region + " still has pending merges in steady state");
                assertFalse(region.state() == RegionState.TRANSIENT, region + " is transient in steady state");
            }
        }
    }
}
