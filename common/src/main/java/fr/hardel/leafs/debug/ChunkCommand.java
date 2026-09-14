package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.holder.WaitReport;
import fr.hardel.leafs.chunk.owner.RegionInbox;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** {@code /leafs chunk [<pos>]}: why the chunk at a block column, the caller's by default, is or is not where the game expects it. */
public final class ChunkCommand {

    private ChunkCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("chunk")
            .executes(context -> report(context.getSource(), ChunkPos.containing(BlockPos.containing(context.getSource().getPosition()))))
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(context -> report(context.getSource(), ColumnPosArgument.getColumnPos(context, "pos").toChunkPos())));
    }

    private static int report(CommandSourceStack source, ChunkPos chunk) {
        int chunkX = chunk.x();
        int chunkZ = chunk.z();
        ServerLevel level = source.getLevel();
        LevelChunks chunks = LevelChunks.of(level);
        LevelRegions regions = LevelRegions.of(level);
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkHolder holder = chunks.holders().table().get(key);

        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("chunk [%d, %d]".formatted(chunkX, chunkZ)).withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("dimension", CommandText.shortDimension(level.dimension().identifier().toString())))
            .append(CommandText.stat("loading level", chunks.graphs().loading().level(key)))
            .append(CommandText.stat("tickets", level.getChunkSource().ticketStorage.getTicketDebugString(key, false))), false);

        if (holder == null) {
            source.sendSuccess(() -> CommandText.gray("no holder: nothing asks for this chunk"), false);
            return 0;
        }

        source.sendSuccess(() -> CommandText.gray(WaitReport.holder(holder)), false);
        source.sendSuccess(() -> Component.empty()
            .append(CommandText.gray(ChunkLevel.fullStatus(holder.getTicketLevel()).toString()))
            .append(CommandText.stat("full", holder.getChunkIfPresent(ChunkStatus.FULL) != null))
            .append(CommandText.stat("ticking", holder.getTickingChunk() != null))
            .append(CommandText.stat("light synced", holder.getSendSyncFuture().isDone()))
            .append(CommandText.stat("sendable", holder.getChunkToSend() != null)), false);
            
        source.sendSuccess(() -> CommandText.gray(owner(chunks, regions, chunkX, chunkZ)), false);
        source.sendSuccess(() -> CommandText.gray(chunks.owners().describeQueued(chunkX, chunkZ)), false);
        return 1;
    }

    /** The thread that took the chunk comes first, a region born over it meanwhile waits for the release. */
    private static String owner(LevelChunks chunks, LevelRegions regions, int chunkX, int chunkZ) {
        String taken = chunks.owners().describeTaken(chunkX, chunkZ);
        if (taken != null) {
            return taken;
        }

        Region<?> region = regions.regionizer().regionAt(chunkX, chunkZ);
        if (region == null) {
            return "owner pool, no region";
        }

        RegionInbox inbox = regions.inboxAt(chunkX, chunkZ);
        return "owner %s with %d queued".formatted(region, inbox == null ? 0 : inbox.size());
    }
}
