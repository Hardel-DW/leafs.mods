package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.metrics.DeferStats;
import fr.hardel.leafs.metrics.ServerMetrics;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** {@code /leafs metrics}: last minute of counters. A FOREIGN refusal rate above zero on a quiet server names an ownership leak. */
public final class MetricsCommand {

    private MetricsCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("metrics").executes(context -> report(context.getSource()));
    }

    private static int report(CommandSourceStack source) {
        ServerMetrics metrics = TickingManager.of(source.getServer()).metrics();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("borrows").withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("fabric events", perMinute(metrics.fabricEventBorrows().perMinute()))), false);

        DeferStats defers = metrics.deferStats();
        for (DeferReason reason : DeferReason.values()) {
            long deferred = defers.deferrals(reason).perMinute();
            long retried = defers.retries(reason).perMinute();
            long dropped = defers.drops(reason).perMinute();
            if (deferred == 0 && retried == 0 && dropped == 0) {
                continue;
            }

            MutableComponent line = Component.empty()
                .append(CommandText.gray("  " + reason.name().toLowerCase(Locale.ROOT).replace('_', ' ')))
                .append(CommandText.stat("deferred", perMinute(deferred)));
            if (retried > 0) {
                line.append(CommandText.stat("retried", perMinute(retried)));
            }

            if (dropped > 0) {
                line.append(CommandText.stat("dropped", perMinute(dropped)));
            }

            source.sendSuccess(() -> line, false);
        }

        for (OwnershipViolationException.Kind kind : OwnershipViolationException.Kind.values()) {
            long region = defers.refusals(kind, DeferStats.RefusalSource.REGION).perMinute();
            long serial = defers.refusals(kind, DeferStats.RefusalSource.SERIAL).perMinute();
            long foreignThread = defers.refusals(kind, DeferStats.RefusalSource.FOREIGN_THREAD).perMinute();
            ChatFormatting color = kind == OwnershipViolationException.Kind.FOREIGN && region + serial + foreignThread > 0
                ? ChatFormatting.RED
                : ChatFormatting.AQUA;
                
            source.sendSuccess(() -> Component.empty()
                .append(Component.literal("refusals " + kind.name().toLowerCase(Locale.ROOT)).withStyle(color))
                .append(CommandText.stat("regions", perMinute(region)))
                .append(CommandText.stat("serial", perMinute(serial)))
                .append(CommandText.stat("threads", perMinute(foreignThread))), false);
        }

        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("flow").withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("packets in", perMinute(metrics.packetsIn().perMinute())))
            .append(CommandText.stat("out", perMinute(metrics.packetsOut().perMinute())))
            .append(CommandText.stat("chunk loads", perMinute(metrics.chunkLoads().perMinute())))
            .append(CommandText.stat("unloads", perMinute(metrics.chunkUnloads().perMinute()))), false);

        return 1;
    }

    private static String perMinute(long count) {
        return count + "/min";
    }

}
