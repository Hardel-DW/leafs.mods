package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.world.ServerLevelWorldAccess;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The scheduled-tick sub-tick counter becomes region-owned. */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "nextSubTickCount", at = @At("HEAD"), cancellable = true)
    private void leafs$regionSubTickCount(CallbackInfoReturnable<Long> callbackInfo) {
        if (this instanceof ServerLevelWorldAccess host && host.leafs$worldData() != null) {
            callbackInfo.setReturnValue(host.leafs$worldData().nextSubTick());
        }
    }
}
