package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** The FULL step publishes the chunk into the live world, so it runs on the position's owner. */
@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {
    @WrapOperation(method = "full", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<Object> leafs$fullOnTheOwner(Supplier<Object> body, Executor pump, Operation<CompletableFuture<Object>> original, @Local(argsOnly = true) WorldGenContext context, @Local(argsOnly = true) ChunkAccess chunk) {
        ServerLevel level = context.level();
        ChunkPos pos = chunk.getPos();
        MinuteCounter completed = TickingManager.of(level.getServer()).metrics().chunksFull();
        Supplier<Object> counted = () -> {
            Object full = body.get();
            completed.increment();
            return full;
        };
        return original.call(counted, LevelChunks.of(level).owners().executor(pos.x(), pos.z()));
    }
}
