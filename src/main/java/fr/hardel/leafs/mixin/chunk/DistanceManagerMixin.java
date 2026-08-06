package fr.hardel.leafs.mixin.chunk;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.util.TriState;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pure read of spawn distance: vanilla's version drains the tracker queue, which only the serial side may run. */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin {

    @Shadow
    @Final
    private DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter;

    @Inject(method = "hasPlayersNearby", at = @At("HEAD"), cancellable = true)
    private void leafs$pureSpawnDistanceRead(long pos, CallbackInfoReturnable<TriState> callbackInfo) {
        int distance = this.naturalSpawnChunkCounter.chunks.get(pos);
        TriState result;
        if (distance <= NaturalSpawner.INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK) {
            result = TriState.TRUE;
        } else {
            result = distance > 8 ? TriState.FALSE : TriState.DEFAULT;
        }

        callbackInfo.setReturnValue(result);
    }
}
