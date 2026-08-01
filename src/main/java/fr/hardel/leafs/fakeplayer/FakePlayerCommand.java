package fr.hardel.leafs.fakeplayer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.hardel.leafs.ticking.LevelTickPhases;
import fr.hardel.leafs.ticking.TickingManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;
import java.util.Locale;

/**
 * DEV-ONLY LOAD-TEST TOOL — never active in a published jar (registration is gated on the
 * development environment). Delete this package plus its two lines in Leafs to remove it entirely.
 */
public final class FakePlayerCommand {

    private static final SuggestionProvider<CommandSourceStack> SCENARIOS = (_, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(BotScenario.values()).map(scenario -> scenario.name().toLowerCase(Locale.ROOT)), builder);

    private FakePlayerCommand() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        TickingManager.installPhases(new LevelTickPhases() {
            @Override
            public void beforeLevelTick(ServerLevel level) {
                FakePlayerManager.tickLevel(level);
            }

            @Override
            public void afterLevelTick(ServerLevel level) {
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> registerTree(dispatcher));
    }

    private static void registerTree(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fakeplayer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("spawn")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                    .then(Commands.argument("spread", IntegerArgumentType.integer(16, 100_000))
                        .executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "count"), IntegerArgumentType.getInteger(context, "spread"), null))
                        .then(Commands.argument("scenario", StringArgumentType.word()).suggests(SCENARIOS)
                            .executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "count"), IntegerArgumentType.getInteger(context, "spread"), BotScenario.valueOf(StringArgumentType.getString(context, "scenario").toUpperCase(Locale.ROOT))))))))
            .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
            .then(Commands.literal("list").executes(context -> list(context.getSource()))));
    }

    private static int spawn(CommandSourceStack source, int count, int spread, BotScenario scenario) {
        int spawned = FakePlayerManager.spawn(source.getServer(), count, spread, scenario);
        source.sendSuccess(() -> Component.literal("Spawned " + spawned + " bot" + (spawned == 1 ? "" : "s") + " within " + spread + " blocks" + (scenario == null ? ", random scenarios" : ", scenario " + scenario)), true);

        return spawned;
    }

    private static int clear(CommandSourceStack source) {
        int removed = FakePlayerManager.clear(source.getServer());
        source.sendSuccess(() -> Component.literal("Removed " + removed + " bots"), true);

        return removed;
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(FakePlayerManager.count() + " bots active"), false);

        return FakePlayerManager.count();
    }
}
