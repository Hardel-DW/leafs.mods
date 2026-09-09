package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.chunk.pool.ChunkTask;
import fr.hardel.leafs.chunk.pool.ReservationBlocks;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.metrics.ServerMetrics;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** {@code /leafs metrics}: last minute of counters. */
public final class MetricsCommand {

    private MetricsCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("metrics").executes(context -> report(context.getSource()));
    }

    private static int report(CommandSourceStack source) {
        TickingManager ticking = TickingManager.of(source.getServer());
        ServerMetrics metrics = ticking.metrics();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("borrows").withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("tick events", perMinute(metrics.tickEventBorrows().perMinute()))), false);

        DeferStats defers = metrics.deferStats();
        for (DeferReason reason : DeferReason.values()) {
            long deferred = defers.deferrals(reason).perMinute();
            long dropped = defers.drops(reason).perMinute();
            if (deferred == 0 && dropped == 0) {
                continue;
            }

            MutableComponent line = Component.empty()
                .append(CommandText.gray("  " + reason.name().toLowerCase(Locale.ROOT).replace('_', ' ')))
                .append(CommandText.stat("deferred", perMinute(deferred)));
            if (dropped > 0) {
                line.append(CommandText.stat("dropped", perMinute(dropped)));
            }

            source.sendSuccess(() -> line, false);
        }

        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("flow").withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("packets in", perMinute(metrics.packetsIn().perMinute())))
            .append(CommandText.stat("out", perMinute(metrics.packetsOut().perMinute())))
            .append(CommandText.stat("chunk loads", perMinute(metrics.chunkLoads().perMinute())))
            .append(CommandText.stat("unloads", perMinute(metrics.chunkUnloads().perMinute()))), false);

        ReservationBlocks blocks = ticking.chunkPool().blocks();
        for (ChunkTask.Kind blocked : ChunkTask.Kind.values()) {
            for (ChunkTask.Kind holder : ChunkTask.Kind.values()) {
                long count = blocks.of(blocked, holder).perMinute();
                if (count > 0) {
                    String pair = blocked.name().toLowerCase(Locale.ROOT) + " behind " + holder.name().toLowerCase(Locale.ROOT);
                    source.sendSuccess(() -> Component.empty().append(CommandText.gray("  pool tasks parked, " + pair)).append(CommandText.stat("", perMinute(count))), false);
                }
            }
        }

        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("players").withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("two threads on one player", perMinute(metrics.sharedPlayers().perMinute()))), false);

        return 1;
    }

    private static String perMinute(long count) {
        return count + "/min";
    }

}
