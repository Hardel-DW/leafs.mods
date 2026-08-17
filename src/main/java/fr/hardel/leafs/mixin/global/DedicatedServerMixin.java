package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.BarrierWindow;
import fr.hardel.leafs.metrics.WindowReason;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The console route: op commands have arbitrary world reach, so they execute in the next barrier window. */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {

    @WrapOperation(method = "handleConsoleInputs", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;performPrefixedCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)V"))
    private void leafs$consoleIntoWindow(Commands commands, CommandSourceStack source, String command, Operation<Void> original) {
        BarrierWindow.of((DedicatedServer) (Object) this).enqueue(WindowReason.CONSOLE_COMMAND, () -> commands.performPrefixedCommand(source, command));
    }
}
