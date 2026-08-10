package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.ownership.Ownership;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.util.TriState;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pure read of spawn distance: vanilla's version drains the tracker queue, which only the serial side may run. */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements PropagatorAccess {

    @Shadow
    @Final
    private DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter;

    @Unique
    private volatile LevelTicketPropagator leafs$propagator;

    @Override
    public LevelTicketPropagator leafs$propagator() {
        return leafs$propagator;
    }

    /** Driving or shadowing exist from the first boot; a plain vanilla run keeps the field null and pays nothing. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createPropagator(CallbackInfo callbackInfo) {
        boolean driving = LeafsConfig.get().ownPropagator();
        if (driving || Ownership.CHECKS_ENABLED) {
            leafs$propagator = new LevelTicketPropagator((DistanceManager) (Object) this, !driving);
        }
    }

    /**
     * The drain point of the loading graph. Driving: the Leafs propagator replaces it and vanilla's
     * two update passes follow on its holders. Shadow: vanilla drains first, the Leafs drain compares.
     * Vanilla alone keeps the budget that caps the measured multi-second backlog spike.
     */
    @WrapOperation(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/LoadingChunkTracker;runDistanceUpdates(I)I"))
    private int leafs$budgetDistanceUpdates(LoadingChunkTracker tracker, int toProcess, Operation<Integer> original, @Local(argsOnly = true) ChunkMap chunkMap) {
        LevelTicketPropagator propagator = leafs$propagator;
        if (propagator != null && !propagator.shadow()) {
            propagator.drain();
            return toProcess;
        }

        int budget = chunkMap.level.getServer().isStopped() ? toProcess : 4096;
        int remaining = toProcess - budget + original.call(tracker, budget);
        if (propagator != null) {
            propagator.drain();
        }

        return remaining;
    }

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
