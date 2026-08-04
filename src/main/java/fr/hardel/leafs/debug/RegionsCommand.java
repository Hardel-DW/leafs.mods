package fr.hardel.leafs.debug;

import com.mojang.brigadier.CommandDispatcher;
import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.LevelTickUnit;
import fr.hardel.leafs.ticking.RegionTickData;
import fr.hardel.leafs.ticking.RegionTickHandle;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.ticking.TickTimings;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> registerTree(dispatcher));
    }

    private static void registerTree(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("regions").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(context -> overview(context.getSource()))
            .then(Commands.argument("dimension", DimensionArgument.dimension())
                .executes(context -> detail(context.getSource(), DimensionArgument.getDimension(context, "dimension")))));
    }

    private static int overview(CommandSourceStack source) {
        List<LevelTickUnit> units = sortedUnits(source);
        long now = System.nanoTime();
        double serverTps = units.isEmpty() ? 0 : units.getFirst().timings().sample(now).tps();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("Leafs ").withStyle(ChatFormatting.GREEN))
            .append(gray(LeafsConfig.get().effectiveRegionThreads() + " workers, server thread "))
            .append(tps(serverTps)), false);

        for (LevelTickUnit unit : units) {
            source.sendSuccess(() -> overviewLine(unit, now), false);
        }

        playerLine(source, now);
        source.sendSuccess(() -> Component.literal("/regions <dimension> shows each region").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);

        return units.size();
    }

    private static Component overviewLine(LevelTickUnit unit, long now) {
        List<Region<RegionTickData>> live = liveRegions(unit.regions());
        MutableComponent line = Component.empty()
            .append(Component.literal(shortDimension(unit.dimension())).withStyle(ChatFormatting.AQUA))
            .append(gray("  regions ")).append(white(live.size()))
            .append(gray("  chunks ")).append(white(unit.chunkCount()))
            .append(gray("  entities ")).append(white(unit.entityCount()))
            .append(gray("  serial ")).append(white(String.format(Locale.ROOT, "%.2fms", unit.timings().sample(now).msptAverage())));

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
            line.append(gray("  slowest ")).append(white("R#" + slowest.id() + " ")).append(tps(slowestTps));
        }

        return line;
    }

    private static int detail(CommandSourceStack source, ServerLevel level) {
        LevelRegions regions = ((ServerLevelRegionAccess) level).leafs$regions();
        List<Region<RegionTickData>> live = liveRegions(regions);
        long now = System.nanoTime();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal(shortDimension(level.dimension().identifier().toString())).withStyle(ChatFormatting.AQUA))
            .append(gray("  regions ")).append(white(live.size()))
            .append(gray("  sections ")).append(white(regions.sections())).append(gray(" (" + regions.deadSections() + " dead)"))
            .append(gray("  created ")).append(white(regions.created()))
            .append(gray("  merged ")).append(white(regions.merged()))
            .append(gray("  split ")).append(white(regions.split()))
            .append(gray("  deferred ")).append(white(regions.deferredHandshakes())), false);

        ServerPlayer player = source.getPlayer();
        Region<RegionTickData> playerRegion = player == null || player.level() != level ? null
            : regions.regionizer().regionAt(player.chunkPosition().x(), player.chunkPosition().z());

        for (Region<RegionTickData> region : live) {
            RegionTickHandle handle = region.data().handle();
            MutableComponent line = Component.empty()
                .append(white(" R#" + region.id() + " "))
                .append(state(region.state()));
            if (handle != null && !handle.isCancelled()) {
                TickTimings.Snapshot timings = handle.timings().sample(now);
                line.append(gray("  ")).append(tps(timings.tps()))
                    .append(gray("  avg ")).append(white(String.format(Locale.ROOT, "%.2fms", timings.msptAverage())))
                    .append(gray("  chunks ")).append(white(handle.chunkCount()))
                    .append(gray("  entities ")).append(white(handle.entityCount()));
            } else {
                line.append(gray("  chunks ")).append(white(region.chunkCount()));
            }

            if (region == playerRegion) {
                line.append(Component.literal("  <- you").withStyle(ChatFormatting.GOLD));
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

        LevelRegions regions = ((ServerLevelRegionAccess) player.level()).leafs$regions();
        Region<RegionTickData> region = regions.regionizer().regionAt(player.chunkPosition().x(), player.chunkPosition().z());
        RegionTickHandle handle = region == null ? null : region.data().handle();
        if (handle == null) {
            return;
        }

        TickTimings.Snapshot timings = handle.timings().sample(now);
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("You ").withStyle(ChatFormatting.GOLD))
            .append(white("R#" + handle.id()))
            .append(gray(" in ")).append(Component.literal(shortDimension(handle.dimension())).withStyle(ChatFormatting.AQUA))
            .append(gray("  ")).append(tps(timings.tps()))
            .append(gray("  avg ")).append(white(String.format(Locale.ROOT, "%.2fms", timings.msptAverage()))), false);
    }

    private static List<LevelTickUnit> sortedUnits(CommandSourceStack source) {
        List<LevelTickUnit> units = new ArrayList<>(((LeafsServerAccess) source.getServer()).leafs$ticking().units());
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

    private static Component gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component white(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
    }
}
