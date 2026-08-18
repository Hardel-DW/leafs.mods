package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.excess.ConcurrentLongSet;
import fr.hardel.leafs.ticking.TickingManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

/**
 * Entity chunk IO off the global thread: the deserializer lane dispatched to the server executor,
 * which lands in the global phase drain, so it moves to the chunk worker pool like the chunk reads.
 * The empty-chunk cache is read by loads on region threads and written by stores, so it goes concurrent.
 */
@Mixin(EntityStorage.class)
public abstract class EntityStorageMixin {

    @Mutable
    @Shadow
    @Final
    private LongSet emptyChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.emptyChunks = new ConcurrentLongSet();
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/util/thread/ConsecutiveExecutor"))
    private ConsecutiveExecutor leafs$deserializeOnTheChunkWorkers(Executor dispatcher, String name, Operation<ConsecutiveExecutor> original, @Local(argsOnly = true) ServerLevel level) {
        return original.call(TickingManager.of(level.getServer()).chunkWorkers(), name);
    }
}
