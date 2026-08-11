package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
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

    /** The per-player loader owns the view and simulation tickets; the vanilla per-player halves disconnect here. */
    @WrapOperation(method = "addPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/TicketStorage;addTicket(Lnet/minecraft/server/level/Ticket;Lnet/minecraft/world/level/ChunkPos;)V"))
    private void leafs$noVanillaSimulationTicket(TicketStorage storage, Ticket ticket, ChunkPos pos, Operation<Void> original) {
    }

    @WrapOperation(method = "removePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/TicketStorage;removeTicket(Lnet/minecraft/server/level/Ticket;Lnet/minecraft/world/level/ChunkPos;)V"))
    private void leafs$noVanillaSimulationTicketRemoval(TicketStorage storage, Ticket ticket, ChunkPos pos, Operation<Void> original) {
    }

    @WrapOperation(method = {"addPlayer", "removePlayer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;update(JIZ)V"))
    private void leafs$noVanillaViewTracker(DistanceManager.PlayerTicketTracker tracker, long pos, int level, boolean added, Operation<Void> original) {
    }

    @WrapOperation(method = "updatePlayerTickets", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;updateViewDistance(I)V"))
    private void leafs$noVanillaViewDistanceSweep(DistanceManager.PlayerTicketTracker tracker, int viewDistance, Operation<Void> original) {
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createPropagator(CallbackInfo callbackInfo) {
        leafs$propagator = new LevelTicketPropagator();
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
