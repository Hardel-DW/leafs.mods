package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.pool.ChunkPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.util.Util$2")
public abstract class BackgroundWorkerMixin {
    @Inject(method = "onStart", at = @At("TAIL"))
    private void leafs$yieldToRegions(CallbackInfo callbackInfo) {
        ChunkPool.yieldToRegions();
    }
}
