package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.holder.GenerationSteps;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenerationChunkHolder.class)
public abstract class GenerationChunkHolderMixin {
    @Inject(method = "rescheduleChunkTask", at = @At("TAIL"))
    private void leafs$startWhatWasScheduled(ChunkMap scheduler, @Nullable ChunkStatus status, CallbackInfo callbackInfo) {
        scheduler.runGenerationTasks();
    }

    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;handle(Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> leafs$releaseACancelledStep(CompletableFuture<ChunkAccess> applied, BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> outcome,
        Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original, @Local(argsOnly = true) ChunkStep step) {
        ChunkStatus status = step.targetStatus();
        BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> released = (chunk, failure) -> {
            if (failure != GenerationSteps.CANCELLED) {
                return outcome.apply(chunk, failure);
            }

            ((GenerationChunkHolder) (Object) this).startedWork.compareAndSet(status, status.getParent());
            return GenerationChunkHolder.UNLOADED_CHUNK;
        };
        return original.call(applied, released);
    }
}
