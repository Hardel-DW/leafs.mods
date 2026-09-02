package fr.hardel.leafs.global;

import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A head execution on the server thread, a command, a join or a leave, borrows a region the moment it touches one of its chunks or entities and returns them all at the end. */
public final class CommandEngine {

    private CommandEngine() {
    }

    /** True when the execution moved: off the server thread it is posted whole to the global phase, one tick later. */
    public static boolean divert(MinecraftServer server, Runnable execution) {
        if (server.isSameThread()) {
            return false;
        }

        TickingManager.of(server).globalScheduler().run(execution);
        return true;
    }

    /** A nested execution shares the head's borrow. */
    public static boolean inHead() {
        return RegionBorrow.current() != null;
    }

    /** The head: the entity it starts from is taken first, the rest at contact. Any other thread already runs under its own ownership rules. */
    public static <T> T runHead(MinecraftServer server, @Nullable Entity first, Supplier<T> execution) {
        if (!server.isSameThread()) {
            return execution.get();
        }

        return head(borrow -> borrowEntity(borrow, first), execution);
    }

    public static void runHead(MinecraftServer server, @Nullable Entity first, Runnable execution) {
        runHead(server, first, () -> {
            execution.run();
            return null;
        });
    }

    /** A command block's whole run, output writes included, with its chunk taken; a block whose chunk left in the meantime stays silent. */
    public static boolean runCommandBlock(ServerLevel level, Vec3 position, BooleanSupplier vanilla) {
        ChunkPos chunk = ChunkPos.containing(BlockPos.containing(position));
        if (divert(level.getServer(), () -> {
            if (level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
                runCommandBlock(level, position, vanilla);
            }
        })) {
            return false;
        }

        if (inHead()) {
            RegionBorrow.current().borrow(LevelRegions.of(level), chunk.x(), chunk.z());
            return vanilla.getAsBoolean();
        }

        return head(borrow -> borrow.borrow(LevelRegions.of(level), chunk.x(), chunk.z()), vanilla::getAsBoolean);
    }

    /** Borrowing is a property of the server thread, in the global phase or inside a level's serial unit alike; the region context stays what it is. */
    /** A reload swaps data every region reads each tick: the server thread takes every region of every level first, or extends the borrow it already holds. */
    public static void runBorrowingAll(MinecraftServer server, Runnable body) {
        if (inHead()) {
            borrowEverything(server, RegionBorrow.current());
            body.run();
            return;
        }

        head(borrow -> borrowEverything(server, borrow), () -> {
            body.run();
            return null;
        });
    }

    private static void borrowEverything(MinecraftServer server, RegionBorrow borrow) {
        for (ServerLevel level : server.getAllLevels()) {
            borrow.borrowAll(LevelRegions.of(level));
        }
    }

    private static <T> T head(Consumer<RegionBorrow> firstContact, Supplier<T> body) {
        return RegionBorrow.hold(borrow -> {
            firstContact.accept(borrow);
            return body.get();
        });
    }

    private static void borrowEntity(RegionBorrow borrow, Entity entity) {
        if (entity != null && entity.level() instanceof ServerLevel level) {
            ChunkPos chunk = entity.chunkPosition();
            borrow.borrow(LevelRegions.of(level), chunk.x(), chunk.z());
        }
    }
}
