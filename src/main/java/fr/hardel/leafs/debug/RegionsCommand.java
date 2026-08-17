package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.LevelTickUnit;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.RegionTickHandle;
import fr.hardel.leafs.ticking.TickTimings;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Server-side region introspection: a per-dimension overview, per-region detail behind a dimension argument. */
public final class RegionsCommand {

    private RegionsCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("regions")
            .executes(context -> overview(context.getSource()))
            .then(Commands.argument("dimension", DimensionArgument.dimension())
                .executes(context -> detail(context.getSource(), DimensionArgument.getDimension(context, "dimension"))));
    }

    private static int overview(CommandSourceStack source) {
        List<LevelTickUnit> units = sortedUnits(source);
        long now = System.nanoTime();
        double serverTps = units.isEmpty() ? 0 : units.getFirst().timings().sample(now).tps();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("Leafs ").withStyle(ChatFormatting.GREEN))
            .append(gray("%d workers, server thread ".formatted(LeafsConfig.get().effectiveThreads())))
            .append(tps(serverTps)), false);

        for (LevelTickUnit unit : units) {
            source.sendSuccess(() -> overviewLine(unit, now), false);
        }

        playerLine(source, now);
        source.sendSuccess(() -> Component.literal("/leafs regions <dimension> shows each region").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);

        return units.size();
    }

    private static Component overviewLine(LevelTickUnit unit, long now) {
        List<Region<RegionTickData>> live = liveRegions(unit.regions());
        MutableComponent line = Component.empty()
            .append(Component.literal(shortDimension(unit.dimension())).withStyle(ChatFormatting.AQUA))
            .append(stat("regions", live.size()))
            .append(stat("chunks", unit.chunkCount()))
            .append(stat("view", unit.viewChunks()))
            .append(stat("entities", unit.entityCount()))
            .append(stat("serial", rate(unit.timings().sample(now))));

        RegionTickHandle slowest = null;
        double slowestTps = Double.MAX_VALUE;
        for (Region<RegionTickData> region : live) {
            RegionTickHandle handle = region.data().handle();
            if (handle != null && !handle.isCancelled()) {
                double regionTps = handle.timings().sample(now).tps();
                if (regionTps < slowestTps) {
                    slowestTps = regionTps;
                    slowest = handle;
                }
            }
        }

        if (slowest != null) {
            line.append(stat("slowest", white("R#" + slowest.id()))).append(sep()).append(tps(slowestTps));
        }

        return line;
    }

    private static int detail(CommandSourceStack source, ServerLevel level) {
        LevelRegions regions = LevelRegions.of(level);
        List<Region<RegionTickData>> live = liveRegions(regions);
        long now = System.nanoTime();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal(shortDimension(level.dimension().identifier().toString())).withStyle(ChatFormatting.AQUA))
            .append(stat("regions", live.size()))
            .append(stat("sections", regions.sections())).append(gray(" (%d dead)".formatted(regions.deadSections())))
            .append(stat("created", regions.created()))
            .append(stat("merged", regions.merged()))
            .append(stat("split", regions.split()))
            .append(stat("deferred", regions.deferredHandshakes())), false);

        ServerPlayer player = source.getPlayer();
        Region<RegionTickData> playerRegion = player == null || player.level() != level ? null
            : regions.regionizer().regionAt(player.chunkPosition().x(), player.chunkPosition().z());

        for (Region<RegionTickData> region : live) {
            RegionTickHandle handle = region.data().handle();
            MutableComponent line = Component.empty()
                .append(sep()).append(white("R#" + region.id()))
                .append(sep()).append(state(region.state()));
            if (handle != null && !handle.isCancelled()) {
                line.append(sep()).append(rate(handle.timings().sample(now)))
                    .append(stat("chunks", handle.chunkCount()))
                    .append(stat("entities", handle.entityCount()));
            } else {
                line.append(stat("chunks", region.chunkCount()));
            }

            if (region == playerRegion) {
                line.append(sep()).append(Component.literal("<- you").withStyle(ChatFormatting.GOLD));
            }

            source.sendSuccess(() -> line, false);
        }

        return live.size();
    }

    private static void playerLine(CommandSourceStack source, long now) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }

        LevelRegions regions = LevelRegions.of(player.level());
        Region<RegionTickData> region = regions.regionizer().regionAt(player.chunkPosition().x(), player.chunkPosition().z());
        RegionTickHandle handle = region == null ? null : region.data().handle();
        if (handle == null) {
            return;
        }

        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("You ").withStyle(ChatFormatting.GOLD))
            .append(white("R#" + handle.id()))
            .append(gray(" in ")).append(Component.literal(shortDimension(handle.dimension())).withStyle(ChatFormatting.AQUA))
            .append(sep()).append(rate(handle.timings().sample(now))), false);
    }

    private static List<LevelTickUnit> sortedUnits(CommandSourceStack source) {
        List<LevelTickUnit> units = new ArrayList<>(TickingManager.of(source.getServer()).units());
        units.sort(Comparator.comparingLong(LevelTickUnit::id));

        return units;
    }

    private static List<Region<RegionTickData>> liveRegions(LevelRegions regions) {
        List<Region<RegionTickData>> live = new ArrayList<>(regions.regionizer().regionsView());
        live.removeIf(region -> region.state() == RegionState.DEAD);
        live.sort(Comparator.comparingLong(Region::id));

        return live;
    }

    private static String shortDimension(String dimension) {
        Identifier location = Identifier.parse(dimension);

        return location.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? location.getPath() : location.toString();
    }

    private static Component rate(TickTimings.Snapshot snapshot) {
        return Component.empty().append(tps(snapshot.tps()))
            .append(stat("avg", String.format(Locale.ROOT, "%.2fms", snapshot.msptAverage())));
    }

    private static Component tps(double value) {
        ChatFormatting color = value >= 19.5 ? ChatFormatting.GREEN : value >= 15 ? ChatFormatting.YELLOW : ChatFormatting.RED;

        return Component.literal(String.format(Locale.ROOT, "%.1f TPS", value)).withStyle(color);
    }

    private static Component state(RegionState value) {
        ChatFormatting color = switch (value) {
            case TICKING -> ChatFormatting.AQUA;
            case READY -> ChatFormatting.GREEN;
            case TRANSIENT -> ChatFormatting.YELLOW;
            case DEAD -> ChatFormatting.DARK_RED;
        };

        return Component.literal(value.name()).withStyle(color);
    }

    /** The single home of the two-space column separator every line of the command uses. */
    private static Component sep() {
        return Component.literal(" ");
    }

    private static Component stat(String label, Object value) {
        return stat(label, white(value));
    }

    private static Component stat(String label, Component value) {
        return Component.empty().append(sep()).append(gray("%s ".formatted(label))).append(value);
    }

    private static Component gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component white(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
    }
}
