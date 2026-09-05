package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.LevelChunks;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** The FULL step publishes into the live world: the pool builds the chunk and only the publication reaches the owner. */
@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {
    @Inject(method = "full", at = @At("HEAD"), cancellable = true)
    private static void leafs$buildOnThePoolPublishOnTheOwner(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callbackInfo) {
        callbackInfo.setReturnValue(LevelChunks.of(context.level()).full().apply(context, chunks, chunk));
    }
}
