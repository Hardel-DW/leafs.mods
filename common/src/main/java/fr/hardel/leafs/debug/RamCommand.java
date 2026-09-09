package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.chunk.LevelChunks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Locale;

/** {@code /leafs ram}: the heap right now, what each collector cost since the JVM started, and per dimension what Leafs holds that must not grow with the terrain visited. */
public final class RamCommand {
    private static final double BYTES_PER_GIGABYTE = 1e9;

    private RamCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("ram").executes(context -> report(context.getSource()));
    }

    private static int report(CommandSourceStack source) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("heap").withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("used", gigabytes(heap.getUsed())))
            .append(CommandText.stat("committed", gigabytes(heap.getCommitted())))
            .append(CommandText.stat("max", gigabytes(heap.getMax()))), false);

        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            source.sendSuccess(() -> Component.empty()
                .append(CommandText.gray("  " + collector.getName()))
                .append(CommandText.stat("collections", collector.getCollectionCount()))
                .append(CommandText.stat("time", String.format(Locale.ROOT, "%.1fs", collector.getCollectionTime() / 1000.0))), false);
        }

        for (ServerLevel level : source.getServer().getAllLevels()) {
            LevelChunks chunks = LevelChunks.of(level);
            source.sendSuccess(() -> Component.empty()
                .append(CommandText.gray("  " + level.dimension().identifier()))
                .append(CommandText.stat("holders", chunks.holders().table().size()))
                .append(CommandText.stat("graph sections", chunks.graphs().sectionCount())), false);
        }

        return 1;
    }

    private static String gigabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f GB", bytes / BYTES_PER_GIGABYTE);
    }
}
