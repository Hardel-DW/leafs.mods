package fr.hardel.leafs.mixin.global;

import fr.hardel.leafs.global.GlobalServerAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseCommandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Command blocks have arbitrary world reach, so their execution moves into the barrier window —
 * exact vanilla semantics, ≤1 tick later (Compromise #4). The optimistic true mirrors vanilla's
 * "command accepted" result; success counts land when the window runs.
 */
@Mixin(BaseCommandBlock.class)
public abstract class BaseCommandBlockMixin {

    @Unique
    private boolean leafs$inWindow;

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void leafs$deferToBarrierWindow(ServerLevel level, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (leafs$inWindow) {
            leafs$inWindow = false;
            return;
        }

        ((GlobalServerAccess) level.getServer()).leafs$barrierWindow().enqueue(() -> {
            leafs$inWindow = true;
            ((BaseCommandBlock) (Object) this).performCommand(level);
        });
        callbackInfo.setReturnValue(true);
    }
}
