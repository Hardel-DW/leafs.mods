package fr.hardel.leafs.mixin.network;

import fr.hardel.leafs.network.SpawnEntityWait;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** The task stays in preparation until the spawn entities are loaded, so the flip never blocks the global thread. */
@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Preparing")
public abstract class PrepareSpawnTaskPreparingMixin {

    @Shadow
    @Final
    private ServerLevel spawnLevel;

    @Shadow
    @Final
    private CompletableFuture<Vec3> spawnPosition;

    /** Anchored on the chunk-stage finish, the last call before vanilla builds the ready state. */
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/progress/LevelLoadListener;finish(Lnet/minecraft/server/level/progress/LevelLoadListener$Stage;)V"), cancellable = true)
    private void leafs$holdForSpawnEntities(CallbackInfoReturnable<Object> callbackInfo) {
        if (SpawnEntityWait.shouldHold(spawnLevel, spawnPosition.join(), PrepareSpawnTask.PREPARE_CHUNK_RADIUS)) {
            callbackInfo.setReturnValue(null);
        }
    }
}
