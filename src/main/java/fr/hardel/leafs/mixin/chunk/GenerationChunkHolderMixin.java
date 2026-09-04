package fr.hardel.leafs.mixin.chunk;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A generation task starts right after the holder registered it, never before, from whichever thread scheduled it. */
@Mixin(GenerationChunkHolder.class)
public abstract class GenerationChunkHolderMixin {
    @Inject(method = "rescheduleChunkTask", at = @At("TAIL"))
    private void leafs$startWhatWasScheduled(ChunkMap scheduler, @Nullable ChunkStatus status, CallbackInfo callbackInfo) {
        scheduler.runGenerationTasks();
    }
}
