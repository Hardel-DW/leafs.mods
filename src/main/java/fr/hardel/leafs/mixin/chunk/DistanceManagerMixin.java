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

/**
 * {@code hasPlayersNearby} is reached from region spawn logic (LocalMobCapCalculator), but vanilla's
 * version drains the tracker queue, a graph mutation only the serial side may run. The read becomes
 * pure; the pending queue drains in the serial {@code runAllUpdates} every tick, so the answer is at
 * most one tick stale, which is vanilla's own cross-tick staleness.
 */
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
