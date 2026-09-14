package fr.hardel.leafs.mixin.global;

import fr.hardel.leafs.global.CommandEngine;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Hook only, global/CommandEngine: every command and function passes here, off the server thread it is posted whole, on it the server thread locks the region of the entity it starts from, the rest at contact. */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "executeCommandInContext", at = @At("HEAD"), cancellable = true)
    private static void leafs$runThroughTheEngine(CommandSourceStack context, Consumer<ExecutionContext<CommandSourceStack>> config, CallbackInfo callbackInfo) {
        if (CommandEngine.divert(context.getServer(), () -> Commands.executeCommandInContext(context, config))) {
            callbackInfo.cancel();
            return;
        }

        Entity entity = context.getEntity();
        if (entity != null) {
            RegionBorrow.atContact(entity);
        }
    }
}
