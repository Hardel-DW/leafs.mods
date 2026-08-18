package fr.hardel.leafs.debug;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

/** The {@code /leafs} root: every mod command mounts under it, gamemaster permission for all. */
public final class LeafsCommand {

    private LeafsCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> dispatcher.register(
            Commands.literal("leafs").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(RegionsCommand.tree())
                .then(TimingsCommand.tree())
                .then(MetricsCommand.tree())
                .then(RecommendationCommand.tree())));
    }
}
