package fr.hardel.leafs.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.LeafsConfig.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** {@code /leafs config [key [value]]}: reads the tunable keys, one of them, or rewrites one in the file for the next start. */
public final class ConfigCommand {

    private ConfigCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("config")
            .executes(context -> show(context.getSource()))
            .then(Commands.argument("key", StringArgumentType.word())
                .suggests((_, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Setting.values()).map(Setting::key), builder))
                .executes(context -> show(context.getSource(), StringArgumentType.getString(context, "key")))
                .then(Commands.argument("value", IntegerArgumentType.integer())
                    .executes(context -> set(context.getSource(), StringArgumentType.getString(context, "key"), IntegerArgumentType.getInteger(context, "value")))));
    }

    private static int show(CommandSourceStack source) {
        LeafsConfig config = LeafsConfig.get();
        for (Setting setting : Setting.values()) {
            source.sendSuccess(() -> CommandText.stat(setting.key(), setting.read(config)), false);
        }

        return Setting.values().length;
    }

    private static int show(CommandSourceStack source, String key) throws CommandSyntaxException {
        Setting setting = setting(key);
        source.sendSuccess(() -> CommandText.stat(setting.key(), setting.read(LeafsConfig.get())), false);
        return 1;
    }

    private static int set(CommandSourceStack source, String key, int value) throws CommandSyntaxException {
        Setting setting = setting(key);
        try {
            LeafsConfig.rewrite(setting, value);
        } catch (IllegalArgumentException exception) {
            throw new SimpleCommandExceptionType(Component.literal(exception.getMessage())).create();
        }

        source.sendSuccess(() -> Component.literal(key + " = " + value + ", applied on the next start").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static Setting setting(String key) throws CommandSyntaxException {
        return Setting.byKey(key).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown key " + key)).create());
    }
}
