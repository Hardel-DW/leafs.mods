package fr.hardel.leafs.network;

import fr.hardel.leafs.ownership.RegionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRoutingFlushScopeTest {

    /** 2026-08-04 lost GUI packets: only a pool region tick batches sends, everyone else keeps vanilla's flush. */
    @Test
    void onlyARegionTickSuspendsTheFlush() {
        assertTrue(PacketRouting.scopedFlush(true));

        RegionContext.enter(new RegionContext.Region(7, "minecraft:overworld"));
        try {
            assertFalse(PacketRouting.scopedFlush(true));
            assertFalse(PacketRouting.scopedFlush(false));
        } finally {
            RegionContext.exit();
        }

        RegionContext.enter(new RegionContext.LevelSerial(1, "minecraft:overworld"));
        try {
            assertTrue(PacketRouting.scopedFlush(true));
        } finally {
            RegionContext.exit();
        }
    }
}
