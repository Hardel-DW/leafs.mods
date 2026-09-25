package fr.hardel.leafs.global;

import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.function.BooleanSupplier;

public final class CommandEngine {

    private CommandEngine() {
    }

    public static boolean divert(MinecraftServer server, Runnable execution) {
        if (TickingManager.of(server).onServerThread()) {
            return false;
        }

        TickingManager.of(server).globalScheduler().run(execution);
        return true;
    }

    public static boolean runCommandBlock(ServerLevel level, BlockPos pos, BooleanSupplier vanilla) {
        ChunkPos chunk = ChunkPos.containing(pos);
        if (divert(level.getServer(), () -> runIfStillLoaded(level, pos, chunk, vanilla))) {
            return false;
        }

        RegionBorrow.atContact(LevelRegions.of(level), chunk.x(), chunk.z());
        return vanilla.getAsBoolean();
    }

    private static void runIfStillLoaded(ServerLevel level, BlockPos pos, ChunkPos chunk, BooleanSupplier vanilla) {
        if (level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
            runCommandBlock(level, pos, vanilla);
        }
    }

    public static void runBorrowingAll(MinecraftServer server, Runnable body) {
        for (ServerLevel level : server.getAllLevels()) {
            RegionBorrow.lockAll(LevelRegions.of(level));
        }

        body.run();
    }
}
