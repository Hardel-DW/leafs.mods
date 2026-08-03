package fr.hardel.leafs.mixin.global;

import fr.hardel.leafs.global.CommandBlockWindow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only - the rail-powered half of the same concern as {@link CommandBlockMixin}. */
@Mixin(MinecartCommandBlock.class)
public abstract class MinecartCommandBlockMixin {

    @Inject(method = "activateMinecart", at = @At("HEAD"), cancellable = true)
    private void leafs$deferToBarrierWindow(ServerLevel level, int x, int y, int z, boolean powered, CallbackInfo callbackInfo) {
        if (CommandBlockWindow.deferMinecartActivation(level, (MinecartCommandBlock) (Object) this, x, y, z, powered)) {
            callbackInfo.cancel();
        }
    }
}
