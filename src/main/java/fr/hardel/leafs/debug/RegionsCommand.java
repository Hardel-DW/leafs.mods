package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.region.RegionState;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        double serverTps = units.isEmpty() ? 0 : units.getFirst().stages().sample(now).tps();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("Leafs ").withStyle(ChatFormatting.GREEN))
            .append(CommandText.gray("%d region workers, server thread ".formatted(LeafsConfig.get().effectiveRegionThreads())))
            .append(CommandText.tps(serverTps)), false);

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
            .append(Component.literal(CommandText.shortDimension(unit.dimension())).withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("regions", live.size()))
            .append(CommandText.stat("chunks", unit.chunkCount()))
            .append(CommandText.stat("entities", unit.entityCount()))
            .append(CommandText.stat("serial", CommandText.rate(unit.stages().sample(now))));

        RegionTickHandle slowest = null;
        double slowestTps = Double.MAX_VALUE;
        for (Region<RegionTickData> region : live) {
            RegionTickHandle handle = region.data().handle();
            if (handle != null && !handle.isCancelled()) {
                double regionTps = handle.stages().sample(now).tps();
                if (regionTps < slowestTps) {
                    slowestTps = regionTps;
                    slowest = handle;
                }
            }
        }

        if (slowest != null) {
            line.append(CommandText.stat("slowest", CommandText.white("R#" + slowest.id()))).append(CommandText.sep()).append(CommandText.tps(slowestTps));
        }

        return line;
    }

    private static int detail(CommandSourceStack source, ServerLevel level) {
        LevelRegions regions = LevelRegions.of(level);
        List<Region<RegionTickData>> live = liveRegions(regions);
        long now = System.nanoTime();
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal(CommandText.shortDimension(level.dimension().identifier().toString())).withStyle(ChatFormatting.AQUA))
            .append(CommandText.stat("regions", live.size()))
            .append(CommandText.stat("sections", regions.sections())).append(CommandText.gray(" (%d dead)".formatted(regions.deadSections())))
            .append(CommandText.stat("created", regions.created()))
            .append(CommandText.stat("destroyed", regions.destroyed()))
            .append(CommandText.stat("merged", regions.merged()))
            .append(CommandText.stat("split", regions.split()))
            .append(CommandText.stat("deferred", regions.deferredHandshakes())), false);

        ServerPlayer player = source.getPlayer();
        Region<RegionTickData> playerRegion = player == null || player.level() != level ? null
            : regions.regionizer().regionAt(player.chunkPosition().x(), player.chunkPosition().z());

        for (Region<RegionTickData> region : live) {
            RegionTickHandle handle = region.data().handle();
            MutableComponent line = Component.empty()
                .append(CommandText.sep()).append(CommandText.white("R#" + region.id()))
                .append(CommandText.sep()).append(state(region.state()));
            if (handle != null && !handle.isCancelled()) {
                line.append(CommandText.sep()).append(CommandText.rate(handle.stages().sample(now)))
                    .append(CommandText.stat("chunks", handle.chunkCount()))
                    .append(CommandText.stat("entities", handle.entityCount()));
            } else {
                line.append(CommandText.stat("chunks", region.chunkCount()));
            }

            if (region == playerRegion) {
                line.append(CommandText.sep()).append(Component.literal("<- you").withStyle(ChatFormatting.GOLD));
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
            .append(CommandText.white("R#" + handle.id()))
            .append(CommandText.gray(" in ")).append(Component.literal(CommandText.shortDimension(handle.dimension())).withStyle(ChatFormatting.AQUA))
            .append(CommandText.sep()).append(CommandText.rate(handle.stages().sample(now))), false);
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

    private static Component state(RegionState value) {
        ChatFormatting color = switch (value) {
            case TICKING -> ChatFormatting.AQUA;
            case READY -> ChatFormatting.GREEN;
            case TRANSIENT -> ChatFormatting.YELLOW;
            case DEAD -> ChatFormatting.DARK_RED;
        };

        return Component.literal(value.name()).withStyle(color);
    }

}
