package fr.hardel.leafs.chunk.core;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.ChunkHoldController;
import fr.hardel.leafs.chunk.MailHold;
import fr.hardel.leafs.chunk.ChunkMailbox;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionContext;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import fr.hardel.leafs.ticking.LeafsWatchdog;
import fr.hardel.leafs.ticking.RegionCrashWriter;
import fr.hardel.leafs.ticking.RegionTickScheduler;
import fr.hardel.leafs.world.RegionWorldData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-08-20: a promotion effect run inline under the drain's area locks posted a ticket into the storage monitor a worker held while waiting on a cell, watchdog kill. */
class ChunkSchedulingTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ChunkScheduling ownedScheduling() {
        LevelRegions regions = new LevelRegions(LeafsConfig.defaults());
        ChunkScheduling scheduling = new ChunkScheduling(null, null, regions, () -> false, null, new ChunkMailbox(new ChunkHoldController() {
            @Override
            public void addHold(int chunkX, int chunkZ, MailHold.Level level) {
            }

            @Override
            public void removeHold(int chunkX, int chunkZ, MailHold.Level level) {
            }
        }));
        regions.simulated(0, 0);
        regions.activate("leafs:test", new RegionTickScheduler(1, false, new LeafsWatchdog(Duration.ofSeconds(60), () -> 0L, _ -> {
        }, _ -> {
        }), new RegionCrashWriter(Path.of("build", "test-crash-reports")), (_, _) -> {
        }), () -> 0L, time -> new RegionWorldData(time, RandomSource.create(), null, new PathTypeCache(), 0L), null);
        RegionContext.enter(new RegionContext.Region(regions.regionizer().regionAt(0, 0).id(), "leafs:test"));
        return scheduling;
    }

    @Test
    void anOwnedEffectStagesUntilTheAreaReleases() {
        ChunkScheduling scheduling = ownedScheduling();
        List<String> order = new ArrayList<>();
        try {
            assertTrue(scheduling.isOwner(0, 0), "the region ticking on this thread owns its chunks");
            scheduling.runOnOwner(0, 0, () -> order.add("inline"));
            assertEquals(List.of("inline"), order, "outside any area, the owner still runs its effects on the spot");

            order.clear();
            scheduling.mutateArea(0, 0, 1, () -> {
                scheduling.runOnOwner(0, 0, () -> order.add("effect"));
                order.add("underTheArea");
            });
        } finally {
            RegionContext.exit();
        }

        assertEquals(List.of("underTheArea", "effect"), order);
    }

    /** 2026-08-29: a mutation that threw left its staging frame on the thread, and every later owner task on that thread was staged for nobody. */
    @Test
    void aFailedMutationClosesItsStagingFrame() {
        ChunkScheduling scheduling = ownedScheduling();
        List<String> order = new ArrayList<>();
        try {
            assertThrows(IllegalStateException.class, () -> scheduling.mutateArea(0, 0, 1, () -> {
                throw new IllegalStateException("mutation failed");
            }));
            scheduling.runOnOwner(0, 0, () -> order.add("inline"));
        } finally {
            RegionContext.exit();
        }

        assertEquals(List.of("inline"), order);
    }
}
