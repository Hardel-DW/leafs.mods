package fr.hardel.leafs.chunk;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A section is chunk content: its owner writes it while other regions search it and the save packs it. */
@ExtendWith(MinecraftBootstrap.class)
class PoiSectionConcurrencyTest {
    private static final int PASSES = 2_000;

    @Test
    void searchesAndPacksSurviveTheOwnerWriting() throws InterruptedException {
        PoiSection section = new PoiSection(() -> { });
        Holder<PoiType> armorer = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.ARMORER);
        Holder<PoiType> butcher = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.BUTCHER);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread owner = Thread.ofPlatform().name("test-owner").uncaughtExceptionHandler((_, e) -> failure.set(e)).start(() -> {
            for (int pass = 0; pass < PASSES; pass++) {
                for (int x = 0; x < 16; x++) {
                    section.add(new BlockPos(x, pass & 15, x), (x & 1) == 0 ? armorer : butcher);
                }

                for (int x = 0; x < 16; x++) {
                    section.remove(new BlockPos(x, pass & 15, x));
                }
            }
        });
        Thread searcher = Thread.ofPlatform().name("test-searcher").uncaughtExceptionHandler((_, e) -> failure.set(e)).start(() -> {
            while (owner.isAlive()) {
                section.getRecords(type -> true, PoiManager.Occupancy.ANY).count();
                section.pack();
            }
        });

        owner.join();
        searcher.join();

        assertNull(failure.get());
        assertEquals(0, section.getRecords(type -> true, PoiManager.Occupancy.ANY).count());
    }
}
