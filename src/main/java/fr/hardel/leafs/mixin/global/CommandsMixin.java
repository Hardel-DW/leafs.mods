package fr.hardel.leafs.mixin.global;

import fr.hardel.leafs.global.ExecutionWindow;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Hook only - global/ExecutionWindow moves any command or function execution triggered off the server thread into the barrier window. */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "executeCommandInContext", at = @At("HEAD"), cancellable = true)
    private static void leafs$executeInWindow(CommandSourceStack context, Consumer<ExecutionContext<CommandSourceStack>> config, CallbackInfo callbackInfo) {
        if (ExecutionWindow.defer(context.getServer(), () -> Commands.executeCommandInContext(context, config))) {
            callbackInfo.cancel();
        }
    }
}
