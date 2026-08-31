package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.core.ChunkScheduling;
import fr.hardel.leafs.chunk.core.GenerationExclusion;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** The FULL step publishes the chunk into the live world, so it runs on the position's owner, under the FULL exclusion radius. */
@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {

    @WrapOperation(method = "full", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<Object> leafs$fullOnTheOwner(Supplier<Object> body, Executor pump, Operation<CompletableFuture<Object>> original, @Local(argsOnly = true) WorldGenContext context, @Local(argsOnly = true) ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkScheduling scheduling = ((PropagatorAccess) context.level().getChunkSource().chunkMap.getDistanceManager()).leafs$propagator().scheduling();
        Supplier<Object> excluded = () -> {
            Object full = scheduling.exclusion().supplyExcluded(pos, GenerationExclusion.FULL_STEP_RADIUS, body);
            TickingManager.of(context.level().getServer()).metrics().chunksFull().increment();
            return full;
        };
        return original.call(excluded, scheduling.ownerExecutor(pos.x(), pos.z()));
    }
}
