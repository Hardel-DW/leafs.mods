package fr.hardel.leafs.mixin.chunk;

import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

/** Vanilla sends the noise and biome fills to Worker-Main; they run on the calling chunk worker. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    @ModifyArg(method = {"fillFromNoise", "createBiomes"}, at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1, require = 2)
    private Executor leafs$inLine(Executor workerMain) {
        return Runnable::run;
    }
}
