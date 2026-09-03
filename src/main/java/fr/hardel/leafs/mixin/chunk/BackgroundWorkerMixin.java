package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.core.ChunkWorkers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Util$2 is vanilla's Worker-Main thread: half the generation, the light and the chunk saves. Same priority as the chunk workers, so the regions win the cores. */
@Mixin(targets = "net.minecraft.util.Util$2")
public abstract class BackgroundWorkerMixin {

    @Inject(method = "onStart", at = @At("TAIL"))
    private void leafs$yieldToRegions(CallbackInfo callbackInfo) {
        ChunkWorkers.yieldToRegions();
    }
}
