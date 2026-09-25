package fr.hardel.leafs.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class LeafsCommand {

    private LeafsCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("leafs").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(RegionsCommand.tree())
            .then(ChunkCommand.tree())
            .then(TimingsCommand.tree())
            .then(MetricsCommand.tree())
            .then(RamCommand.tree())
            .then(ConfigCommand.tree())
            .then(CrashCommand.tree());
    }
}
