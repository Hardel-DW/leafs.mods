package fr.hardel.leafs.debug;

import fr.hardel.leafs.metrics.StageTimings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** The shared text vocabulary of the {@code /leafs} commands: columns, stats, colors, TPS coloring. */
final class CommandText {

    private CommandText() {
    }

    static String shortDimension(String dimension) {
        Identifier location = Identifier.parse(dimension);

        return location.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? location.getPath() : location.toString();
    }

    static Component rate(StageTimings.Snapshot snapshot) {
        return Component.empty().append(tps(snapshot.tps()))
            .append(stat("avg", String.format(Locale.ROOT, "%.2fms", snapshot.msptAverage())));
    }

    static Component tps(double value) {
        ChatFormatting color = value >= 19.5 ? ChatFormatting.GREEN : value >= 15 ? ChatFormatting.YELLOW : ChatFormatting.RED;

        return Component.literal(String.format(Locale.ROOT, "%.1f TPS", value)).withStyle(color);
    }

    static Component sep() {
        return Component.literal(" ");
    }

    static Component stat(String label, Object value) {
        return stat(label, white(value));
    }

    static Component stat(String label, Component value) {
        return Component.empty().append(sep()).append(gray("%s ".formatted(label))).append(value);
    }

    static Component gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    static Component white(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
    }
}
