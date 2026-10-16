package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class GameThreadTest {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private final Map<ChunkPos, String> seen = new ConcurrentHashMap<>();

    public GameThreadTest() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, _) -> seen.put(chunk.getPos(), identity(level.getServer())));
    }

    /** 2026-10-16: Open Parties and Claims crashed, its entity hook ran on a chunk worker building a FULL chunk that answered isSameThread false.
     * A ticket alone, so the server thread never builds the chunk itself. */
    @GameTest(maxTicks = HangGuard.TICKS)
    public void aModHookOfAChunkBuildSeesTheServerThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos origin = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
        ChunkPos far = new ChunkPos(origin.x() + 96, origin.z());
        level.getChunkSource().updateChunkForced(far, true);

        helper.succeedWhen(() -> {
            helper.assertTrue(seen.containsKey(far), "CHUNK_LOAD fired for %s".formatted(far));
            helper.assertValueEqual(seen.get(far), "server true, packets true, named server", "identity seen by the CHUNK_LOAD of %s".formatted(far));
            level.getChunkSource().updateChunkForced(far, false);
        });
    }

    @GameTest(maxTicks = HangGuard.TICKS)
    public void aThreadOutsideTheGameSeesTheVanillaAnswers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(CHEST, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(CHEST);
        ChunkPos chunk = ChunkPos.containing(chest);
        AtomicReference<String> answers = new AtomicReference<>();
        Runnable read = () -> answers.set("block entity %s, chunk %s".formatted(level.getBlockEntity(chest), level.getChunkSource().getChunkNow(chunk.x(), chunk.z())));
        new Thread(read, "leafs-gametest-mod").start();

        helper.succeedWhen(() -> {
            helper.assertTrue(answers.get() != null, "the mod thread has read");
            helper.assertValueEqual(answers.get(), "block entity null, chunk null", "answers off the game threads");
        });
    }

    private static String identity(MinecraftServer server) {
        String name = Thread.currentThread().getName().toLowerCase(Locale.ROOT).contains("server") ? "named server" : "not named server";
        return "server %s, packets %s, %s".formatted(server.isSameThread(), server.packetProcessor().isSameThread(), name);
    }
}
