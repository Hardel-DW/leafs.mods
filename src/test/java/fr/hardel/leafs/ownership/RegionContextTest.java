package fr.hardel.leafs.ownership;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionContextTest {

    @AfterEach
    void clearContext() {
        RegionContext.exit();
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
        RegionContext.enter(new RegionContext.LevelSerial(1, "minecraft:overworld"));

        IllegalStateException nested = assertThrows(IllegalStateException.class, () -> RegionContext.enter(new RegionContext.Region(1, "minecraft:the_nether")));
        assertTrue(nested.getMessage().contains("level-serial unit #1 in minecraft:overworld"));
    }
}
