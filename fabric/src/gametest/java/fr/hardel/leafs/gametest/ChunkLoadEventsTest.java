package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkLoadEventsTest {
    private final Set<ChunkPos> loaded = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> generated = ConcurrentHashMap.newKeySet();

    public ChunkLoadEventsTest() {
        ServerChunkEvents.CHUNK_LOAD.register((_, chunk, _) -> loaded.add(chunk.getPos()));
        ServerChunkEvents.CHUNK_GENERATE.register((_, chunk) -> generated.add(chunk.getPos()));
    }

    @GameTest(maxTicks = 100)
    public void aChunkLoadedByAModFiresTheLoadEvents(GameTestHelper helper) {
        ChunkPos origin = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        ChunkPos far = new ChunkPos(origin.x() + 64, origin.z());
        helper.getLevel().getChunk(far.x(), far.z());

        helper.succeedWhen(() -> {
            helper.assertTrue(loaded.contains(far), "CHUNK_LOAD fired for %s".formatted(far));
            helper.assertTrue(generated.contains(far), "CHUNK_GENERATE fired for %s".formatted(far));
        });
    }
}
