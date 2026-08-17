package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.hardel.leafs.global.GlobalServerAccess;
import fr.hardel.leafs.global.LeafsGameRules;
import fr.hardel.leafs.global.WindowPressure;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** {@code /leafs recommendation}: suggests gamerule changes that improve parallelism. */
public final class RecommendationCommand {

    private static final long RECENT_PRESSURE_NANOS = TimeUnit.SECONDS.toNanos(10);

    private RecommendationCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("recommendation").executes(context -> report(context.getSource()));
    }

    private static int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<Recommendation> found = new ArrayList<>();
        tickFunctions(server).ifPresent(found::add);
        repeatingCommandBlocks(server).ifPresent(found::add);

        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nothing keeps the barrier window busy, no recommendation.").withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        for (Recommendation recommendation : found) {
            source.sendSuccess(recommendation::problem, false);
            source.sendSuccess(recommendation::action, false);
        }

        return found.size();
    }

    private static Optional<Recommendation> tickFunctions(MinecraftServer server) {
        int functions = server.getFunctions().ticking.size();
        if (functions == 0 || !server.getGameRules().get(LeafsGameRules.tickFunctionsWork)) {
            return Optional.empty();
        }

        return Optional.of(new Recommendation(
            problem("%d %s in #minecraft:tick pause every region each tick.".formatted(functions, functions == 1 ? "function" : "functions")),
            action("/gamerule %s false".formatted(LeafsGameRules.tickFunctionsWork.id()), "cuts the loop, /function and #load keep working")));
    }

    private static Optional<Recommendation> repeatingCommandBlocks(MinecraftServer server) {
        if (!server.getGameRules().get(LeafsGameRules.repeatingCommandBlocksWork)) {
            return Optional.empty();
        }

        WindowPressure pressure = ((GlobalServerAccess) server).leafs$windowPressure();
        long last = pressure.lastRepeatingDeferralNanos();
        if (last == 0 || System.nanoTime() - last > RECENT_PRESSURE_NANOS) {
            return Optional.empty();
        }

        return Optional.of(new Recommendation(
            problem("Repeating command blocks pause every region each tick, %d executions since startup.".formatted(pressure.repeatingDeferrals())),
            action("/gamerule %s false".formatted(LeafsGameRules.repeatingCommandBlocksWork.id()), "skips them but keeps them armed, impulse and chain blocks keep working")));
    }

    private static Component problem(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static Component action(String command, String effect) {
        return Component.literal("> %s".formatted(command))
            .withStyle(style -> style.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.SuggestCommand(command)))
            .append(Component.literal("  %s".formatted(effect)).withStyle(ChatFormatting.GRAY));
    }

    private record Recommendation(Component problem, Component action) {
    }
}
