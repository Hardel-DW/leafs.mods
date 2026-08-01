package fr.hardel.leafs.debug;

import com.mojang.brigadier.CommandDispatcher;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.LevelTickUnit;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.ticking.TickTimings;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Server-side region introspection: one text line per tick unit, the M10 debugging surface. */
public final class RegionsCommand {

    private RegionsCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> registerTree(dispatcher));
    }

    private static void registerTree(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("regions").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> report(context.getSource())));
    }

    private static int report(CommandSourceStack source) {
        List<LevelTickUnit> units = new ArrayList<>(((LeafsServerAccess) source.getServer()).leafs$ticking().units());
        units.sort(Comparator.comparingLong(LevelTickUnit::id));
        long now = System.nanoTime();

        /* Attached mode: one thread ticks every unit in sequence, so their rate IS the server's — stated once
           rather than repeated per line, where it would read as independent measurements. */
        double tickRate = units.isEmpty() ? 0 : units.getFirst().timings().sample(now).tps();
        String header = String.format(Locale.ROOT, "Leafs — attached mode (1 game thread, ticks units in sequence), %.1f TPS, %d tick unit%s",
            tickRate, units.size(), units.size() == 1 ? "" : "s");
        source.sendSuccess(() -> Component.literal(header), false);

        ServerPlayer player = source.getPlayer();
        Region<Void> playerRegion = player == null ? null
            : ((ServerLevelRegionAccess) player.level()).leafs$regions().regionizer().regionAt(player.chunkPosition().x(), player.chunkPosition().z());

        for (LevelTickUnit unit : units) {
            TickTimings.Snapshot timings = unit.timings().sample(now);
            String line = String.format(Locale.ROOT, "#%d %s — tick %d, %.2f/%.2f/%.2f/%.2fms p50/p95/p99/max (avg %.2f), %d chunks, %d entities",
                unit.id(), unit.dimension(), unit.currentTick(), timings.mspt50(), timings.mspt95(), timings.mspt99(),
                timings.msptMax(), timings.msptAverage(), unit.chunkCount(), unit.entityCount());
            source.sendSuccess(() -> Component.literal(line), false);
            reportRegions(source, unit, playerRegion);
        }

        return units.size();
    }

    /** Regions of one unit, sorted by id: the summary line first, then one line per live region. */
    private static void reportRegions(CommandSourceStack source, LevelTickUnit unit, Region<Void> playerRegion) {
        LevelRegions regions = unit.regions();
        List<Region<Void>> live = new ArrayList<>(regions.regionizer().regionsView());
        live.sort(Comparator.comparingLong(Region::id));

        String summary = String.format(Locale.ROOT, "  regions %d | sections %d (%d dead) | chunks %d tracked / %d loaded (census) | created %d, merged %d, split %d, deferred %d",
            live.size(), regions.sections(), regions.deadSections(), unit.trackedChunks(), unit.chunkCount(),
            regions.created(), regions.merged(), regions.split(), regions.deferredHandshakes());
        source.sendSuccess(() -> Component.literal(summary), false);

        for (Region<Void> region : live) {
            String line = String.format(Locale.ROOT, "    R#%d %s  %d sections, %d chunks%s",
                region.id(), region.state(), region.sectionCount(), region.chunkCount(), region == playerRegion ? "  <- you" : "");
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }
}
