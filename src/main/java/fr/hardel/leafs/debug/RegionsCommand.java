package fr.hardel.leafs.debug;

import com.mojang.brigadier.CommandDispatcher;
import fr.hardel.leafs.ticking.LevelTickUnit;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.TickTimings;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
        source.sendSuccess(() -> Component.literal("Leafs regions — attached mode, " + units.size() + " tick unit" + (units.size() == 1 ? "" : "s")), false);

        long now = System.nanoTime();
        for (LevelTickUnit unit : units) {
            TickTimings.Snapshot timings = unit.timings().sample(now);
            String line = String.format(Locale.ROOT, "#%d %s — tick %d, %.1f TPS, %.2fms avg / %.2fms max, %d chunks, %d entities", unit.id(), unit.dimension(), unit.currentTick(), timings.tps(), timings.msptAverage(), timings.msptMax(), unit.chunkCount(), unit.entityCount());
            source.sendSuccess(() -> Component.literal(line), false);
        }

        return units.size();
    }
}
