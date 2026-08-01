package fr.hardel.leafs.ownership;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipTest {

    @AfterEach
    void clearContext() {
        RegionContext.exit();
    }

    @Test
    void checksAreEnabledInTests() {
        assertTrue(Ownership.CHECKS_ENABLED, "tests must run with -Dleafs.asserts=true");
    }

    @Test
    void threadsStartWithoutContext() {
        assertNull(RegionContext.current());
    }

    @Test
    void enterAndExitDriveCurrent() {
        RegionContext.enter(new RegionContext.Region(3, "minecraft:overworld"));

        assertEquals("region #3 in minecraft:overworld", RegionContext.current().describe());

        RegionContext.exit();
        assertNull(RegionContext.current());
    }

    @Test
    void nestedEnterIsABug() {
        RegionContext.enter(RegionContext.GLOBAL);

        assertThrows(IllegalStateException.class, () -> RegionContext.enter(new RegionContext.Region(1, "minecraft:the_nether")));
    }

    @Test
    void assertGlobalAcceptsOnlyTheGlobalContext() {
        RegionContext.enter(RegionContext.GLOBAL);
        assertDoesNotThrow(Ownership::assertGlobal);
        RegionContext.exit();

        OwnershipViolationException violation = assertThrows(OwnershipViolationException.class, Ownership::assertGlobal);
        assertTrue(violation.getMessage().contains("outside the region system"));

        RegionContext.enter(new RegionContext.Region(7, "minecraft:overworld"));
        assertThrows(OwnershipViolationException.class, Ownership::assertGlobal);
    }

    @Test
    void assertRegionThreadAcceptsOnlyRegionContexts() {
        RegionContext.enter(new RegionContext.Region(1, "minecraft:overworld"));
        assertDoesNotThrow(Ownership::assertRegionThread);
        RegionContext.exit();

        RegionContext.enter(RegionContext.GLOBAL);
        OwnershipViolationException violation = assertThrows(OwnershipViolationException.class, Ownership::assertRegionThread);
        assertTrue(violation.getMessage().contains("global phase"));
        assertTrue(violation.getMessage().contains(Thread.currentThread().getName()));
    }
}
