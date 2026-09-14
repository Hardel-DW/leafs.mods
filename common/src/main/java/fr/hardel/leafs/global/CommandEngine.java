package fr.hardel.leafs.global;

import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

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

    public static boolean runCommandBlock(ServerLevel level, Vec3 position, BooleanSupplier vanilla) {
        ChunkPos chunk = ChunkPos.containing(BlockPos.containing(position));
        if (divert(level.getServer(), () -> {
            if (level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
                runCommandBlock(level, position, vanilla);
            }
        })) {
            return false;
        }

        RegionBorrow.atContact(LevelRegions.of(level), chunk.x(), chunk.z());
        return vanilla.getAsBoolean();
    }

    public static void runBorrowingAll(MinecraftServer server, Runnable body) {
        RegionBorrow borrow = RegionBorrow.current();
        for (ServerLevel level : server.getAllLevels()) {
            borrow.borrowAll(LevelRegions.of(level));
        }

        body.run();
    }
}
