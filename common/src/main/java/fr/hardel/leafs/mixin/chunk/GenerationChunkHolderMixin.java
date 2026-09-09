package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.LevelChunks;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

import java.util.concurrent.CompletableFuture;
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

    /** The step lands on its holder through Leafs, which knows one more outcome than vanilla: a step cancelled in the queue. */
    @WrapMethod(method = "applyStep")
    private CompletableFuture<ChunkResult<ChunkAccess>> leafs$stepOutcome(ChunkStep step, GeneratingChunkMap chunkMap, StaticCache2D<GenerationChunkHolder> cache, Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        return LevelChunks.of(((ChunkMap) chunkMap).level).steps().applyOnHolder((GenerationChunkHolder) (Object) this, step, cache);
    }
}
