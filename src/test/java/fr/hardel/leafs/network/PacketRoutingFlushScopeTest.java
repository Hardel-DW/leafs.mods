package fr.hardel.leafs.network;

import fr.hardel.leafs.ownership.RegionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRoutingFlushScopeTest {

    /**
     * The 2026-08-04 lost-GUI-packet bug: the level-serial unit carried a plain Region context, so
     * every send from the packet-drain phase lost its per-packet flush. Only a pool region tick
     * batches; the serial phase and unowned threads keep vanilla's flush cadence.
     */
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
