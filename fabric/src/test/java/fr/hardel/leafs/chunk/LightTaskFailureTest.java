package fr.hardel.leafs.chunk;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import fr.hardel.MinecraftBootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MinecraftBootstrap.class)
class LightTaskFailureTest {
    /** 2026-09-24: a light task that threw never completed its chunk, so the light ticket kept the chunk and its ring loaded until the restart. */
    @Test
    void aThrowingLightTaskStillCompletesItsChunk() {
        StarLightInterface light = new StarLightInterface(null, true, true, null);
        light.scheduleChunkLight(new ChunkPos(0, 0), () -> {
            throw new IllegalStateException("light failed");
        });
        CompletableFuture<Void> synced = light.syncFuture(0, 0);
        assertThrows(IllegalStateException.class, light::propagateChanges);
        assertTrue(synced.isDone());
    }
}
