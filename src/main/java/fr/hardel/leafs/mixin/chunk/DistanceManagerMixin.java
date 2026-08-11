package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.ViewAdmissionAccess;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
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

    @Shadow
    @Final
    private ThrottlingChunkTaskDispatcher ticketDispatcher;

    @Unique
    private volatile LevelTicketPropagator leafs$propagator;

    /** One view-ticket admission per connected player, vanilla floor of 4; see ThrottlingChunkTaskDispatcherMixin. */
    @Inject(method = "runAllUpdates", at = @At("HEAD"))
    private void leafs$scaleViewAdmission(ChunkMap chunkMap, CallbackInfoReturnable<Boolean> callbackInfo) {
        ((ViewAdmissionAccess) ticketDispatcher).leafs$admissionCap(chunkMap.level.getServer().getPlayerCount());
    }

    @Override
    public LevelTicketPropagator leafs$propagator() {
        return leafs$propagator;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createPropagator(CallbackInfo callbackInfo) {
        leafs$propagator = new LevelTicketPropagator((DistanceManager) (Object) this);
    }

    /** The Leafs propagator replaces vanilla's budgeted graph; vanilla's drain stays as the net for pre-binding strays and runs empty. */
    @WrapOperation(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/LoadingChunkTracker;runDistanceUpdates(I)I"))
    private int leafs$drainPropagator(LoadingChunkTracker tracker, int toProcess, Operation<Integer> original) {
        leafs$propagator.drain();

        return original.call(tracker, toProcess);
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
