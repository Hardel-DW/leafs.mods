package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.commands.TickCommand;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Locale;

// Each region keeps its own tick time, so the query only shows what the whole game shares.
@Mixin(TickCommand.class)
public abstract class TickCommandMixin {
    @WrapMethod(method = "tickQuery")
    private static int leafs$queryTheSharedState(CommandSourceStack source, Operation<Integer> original) {
        ServerTickRateManager manager = source.getServer().tickRateManager();
        String status = manager.isSprinting() ? "commands.tick.status.sprinting" : manager.isFrozen() ? "commands.tick.status.frozen" : "commands.tick.status.running";
        String rate = "Target tick rate: %s per second, %s ms per tick. Each region keeps its own tick time: /leafs regions"
            .formatted(String.format(Locale.ROOT, "%.1f", manager.tickrate()), String.format(Locale.ROOT, "%.1f", manager.millisecondsPerTick()));
            
        source.sendSuccess(() -> Component.translatable(status), false);
        source.sendSuccess(() -> Component.literal(rate), false);
        return (int) manager.tickrate();
    }
}
