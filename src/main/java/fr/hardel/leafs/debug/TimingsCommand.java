package fr.hardel.leafs.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages.TickFamily;
import fr.hardel.leafs.metrics.TickStages.TickStage;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.LevelTickUnit;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.RegionTickHandle;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/** {@code /leafs timings}: per-stage cost of one tick unit, averaged over the last five seconds. */
public final class TimingsCommand {
    private static final int AVERAGE_WINDOW_TICKS = 100;

    private TimingsCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("timings")
            .executes(context -> global(context.getSource()))
            .then(Commands.argument("dimension", DimensionArgument.dimension())
                .executes(context -> serial(context.getSource(), DimensionArgument.getDimension(context, "dimension")))
                .then(Commands.argument("region", IntegerArgumentType.integer(0))
                    .executes(context -> region(context.getSource(), DimensionArgument.getDimension(context, "dimension"), IntegerArgumentType.getInteger(context, "region")))));
    }

    private static int global(CommandSourceStack source) {
        StageTimings stages = TickingManager.of(source.getServer()).metrics().globalStages();
        long[] averages = stages.averageNanos(AVERAGE_WINDOW_TICKS);
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("server thread").withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("stages", formatMillis(sum(averages)))), false);

        return sendStages(source, TickFamily.GLOBAL, averages);
    }

    private static int serial(CommandSourceStack source, ServerLevel level) {
        LevelTickUnit unit = TickingManager.of(source.getServer()).unitOf(level);
        if (unit == null) {
            source.sendFailure(Component.literal("This dimension has not ticked yet"));
            return 0;
        }

        long[] averages = unit.stages().averageNanos(AVERAGE_WINDOW_TICKS);
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("serial " + CommandText.shortDimension(unit.dimension())).withStyle(ChatFormatting.AQUA))
            .append(CommandText.sep()).append(CommandText.rate(unit.stages().sample(System.nanoTime()))), false);

        return sendStages(source, TickFamily.SERIAL, averages);
    }

    private static int region(CommandSourceStack source, ServerLevel level, int regionId) {
        RegionTickHandle handle = null;
        for (Region<RegionTickData> region : LevelRegions.of(level).regionizer().regionsView()) {
            if (region.id() == regionId) {
                handle = region.data().handle();
            }
        }

        if (handle == null || handle.isCancelled()) {
            source.sendFailure(Component.literal("No live region #" + regionId + " in this dimension, /leafs regions lists them"));
            return 0;
        }

        long[] averages = handle.stages().averageNanos(AVERAGE_WINDOW_TICKS);
        RegionTickHandle region = handle;
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("R#" + region.id() + " " + CommandText.shortDimension(region.dimension())).withStyle(ChatFormatting.AQUA))
            .append(CommandText.sep()).append(CommandText.rate(region.stages().sample(System.nanoTime())))
            .append(CommandText.stat("chunks", region.chunkCount()))
            .append(CommandText.stat("entities", region.entityCount()))
            .append(CommandText.stat("lag", formatMillis(averageLag(region.stages())))), false);

        return sendStages(source, TickFamily.REGION, averages);
    }

    private static int sendStages(CommandSourceStack source, TickFamily family, long[] averages) {
        for (TickStage stage : TickStages.of(family)) {
            long nanos = averages[stage.index()];
            source.sendSuccess(() -> Component.empty()
                .append(CommandText.gray("  " + stage.id() + " "))
                .append(CommandText.white(formatMillis(nanos))), false);
        }

        return TickStages.count(family);
    }

    /** Lag has no ring, so this is the average since the region was born, not over the last five seconds like the stages. */
    private static long averageLag(StageTimings stages) {
        int ticks = stages.completedTicks();
        return ticks == 0 ? 0 : stages.lagNanos() / ticks;
    }

    private static long sum(long[] averages) {
        long total = 0;
        for (long value : averages) {
            total += value;
        }

        return total;
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.2fms", nanos / 1_000_000.0);
    }
}
