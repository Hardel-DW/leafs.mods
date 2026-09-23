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

import java.util.List;

public final class ChunkCommand {

    private ChunkCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("chunk")
            .executes(context -> send(context.getSource(), ChunkPos.containing(BlockPos.containing(context.getSource().getPosition()))))
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(context -> send(context.getSource(), ColumnPosArgument.getColumnPos(context, "pos").toChunkPos())));
    }

    // Used by the Leafs Debug mod
    public static List<Component> report(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunks chunks = LevelChunks.of(level);
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkHolder holder = chunks.holders().table().get(key);
        Component header = Component.empty()
            .append(Component.literal("chunk [%d, %d]".formatted(chunkX, chunkZ)).withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("dimension", level.dimension().identifier().toShortString()))
            .append(CommandText.stat("loading level", chunks.graphs().loading().level(key)))
            .append(CommandText.stat("tickets", level.getChunkSource().ticketStorage.getTicketDebugString(key, false)));

        if (holder == null) {
            return List.of(header, CommandText.gray("no holder: nothing asks for this chunk"));
        }

        Component status = Component.empty()
            .append(CommandText.gray(ChunkLevel.fullStatus(holder.getTicketLevel()).toString()))
            .append(CommandText.stat("full", holder.getChunkIfPresent(ChunkStatus.FULL) != null))
            .append(CommandText.stat("ticking", holder.getTickingChunk() != null))
            .append(CommandText.stat("light synced", holder.getSendSyncFuture().isDone()))
            .append(CommandText.stat("sendable", holder.getChunkToSend() != null));

        return List.of(header, CommandText.gray(WaitReport.holder(holder)), status,
            CommandText.gray(owner(chunks, LevelRegions.of(level), chunkX, chunkZ)), CommandText.gray(chunks.owners().describeQueued(chunkX, chunkZ)));
    }

    private static int send(CommandSourceStack source, ChunkPos chunk) {
        List<Component> lines = report(source.getLevel(), chunk.x(), chunk.z());
        lines.forEach(line -> source.sendSuccess(() -> line, false));
        return lines.size();
    }

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
