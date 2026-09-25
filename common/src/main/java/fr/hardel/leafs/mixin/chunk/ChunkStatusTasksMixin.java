package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.LevelChunks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {

    @WrapOperation(method = "full", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<ChunkAccess> leafs$publishOnTheOwner(Supplier<ChunkAccess> body, Executor mainThread, Operation<CompletableFuture<ChunkAccess>> original,
        @Local(argsOnly = true) WorldGenContext context, @Local(argsOnly = true) ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        return original.call(body, LevelChunks.of(context.level()).publisher(pos.x(), pos.z()));
    }
}
