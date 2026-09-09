package fr.hardel.leafs.chunk;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Block states read from any thread while the owner writes; writers and serializers must not crash each other. */
@ExtendWith(MinecraftBootstrap.class)
class PalettedContainerConcurrencyTest {

    private static List<BlockState> states(int count) {
        List<BlockState> states = new ArrayList<>(count);
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            if (states.size() == count) {
                return states;
            }

            states.add(state);
        }

        throw new IllegalStateException("Registry holds fewer than " + count + " block states");
    }

    /** 2026-08-19: vanilla's ThreadingDetector crashes the second entrant; a region writing while IO packs must serialize instead. */
    @Test
    void writerAndSerializerShareTheContainerWithoutCrashing() throws InterruptedException {
        List<BlockState> states = states(64);
        Strategy<BlockState> strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> container = new PalettedContainer<>(states.getFirst(), strategy);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = Thread.ofPlatform().name("test-region-writer").uncaughtExceptionHandler((t, e) -> failure.set(e)).start(() -> {
            for (int pass = 0; pass < 200; pass++) {
                for (int index = 0; index < 4096; index++) {
                    container.set(index & 15, index >> 8, index >> 4 & 15, states.get((index + pass) & 63));
                }
            }
        });
        Thread serializer = Thread.ofPlatform().name("test-io-packer").uncaughtExceptionHandler((t, e) -> failure.set(e)).start(() -> {
            while (writer.isAlive()) {
                container.pack(strategy);
            }
        });

        writer.join();
        serializer.join();
        assertNull(failure.get());

        for (int index = 0; index < 4096; index++) {
            assertEquals(states.get((index + 199) & 63), container.get(index & 15, index >> 8, index >> 4 & 15));
        }
    }

    /** 2026-08-19: an overflow must resize without growing the palette a reader still snapshots. */
    @Test
    void overflowResizesWithoutTouchingThePublishedPalette() {
        HashMapPalette<String> palette = new HashMapPalette<>(2, List.of("a", "b", "c", "d"));

        int resized = palette.idFor("e", (bits, value) -> {
            assertEquals(3, bits);
            assertEquals("e", value);
            return 99;
        });

        assertEquals(99, resized);
        assertEquals(4, palette.getSize());
    }
}
