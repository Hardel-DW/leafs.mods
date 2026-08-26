package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.PropagatorAccess;
import fr.hardel.leafs.chunk.SpawnProximity;
import fr.hardel.leafs.chunk.propagator.LevelTicketPropagator;
import fr.hardel.leafs.chunk.propagator.SimulationLevels;
import fr.hardel.excess.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.level.ServerPlayer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The three distance authorities hang here: loading propagator, simulation levels, spawn proximity. Vanilla's graphs stay unfed and run empty. */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements PropagatorAccess {

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk;

    @Unique
    private volatile LevelTicketPropagator leafs$propagator;

    @Unique
    private volatile SimulationLevels leafs$simulation;

    @Unique
    private volatile SpawnProximity leafs$spawnProximity;

    @Override
    public LevelTicketPropagator leafs$propagator() {
        return leafs$propagator;
    }

    @Override
    public SimulationLevels leafs$simulation() {
        return leafs$simulation;
    }

    @Override
    public SpawnProximity leafs$spawnProximity() {
        return leafs$spawnProximity;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createAuthorities(CallbackInfo callbackInfo) {
        playersPerChunk = new ConcurrentLong2ObjectMap<>();
        leafs$propagator = new LevelTicketPropagator();
        leafs$simulation = new SimulationLevels();
        leafs$spawnProximity = new SpawnProximity();
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

    /** The spawn disk marks directly at vanilla's own feed sites, instead of waking the serial graph. */
    @WrapOperation(method = "addPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$FixedPlayerDistanceChunkTracker;update(JIZ)V"))
    private void leafs$spawnDiskEnter(DistanceManager.FixedPlayerDistanceChunkTracker tracker, long pos, int level, boolean added, Operation<Void> original) {
        leafs$spawnProximity.add(ChunkPos.getX(pos), ChunkPos.getZ(pos));
    }

    @WrapOperation(method = "removePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager$FixedPlayerDistanceChunkTracker;update(JIZ)V"))
    private void leafs$spawnDiskLeave(DistanceManager.FixedPlayerDistanceChunkTracker tracker, long pos, int level, boolean added, Operation<Void> original) {
        leafs$spawnProximity.remove(ChunkPos.getX(pos), ChunkPos.getZ(pos));
    }

    /** The serial net: both Leafs authorities drain here too, covering tickets posted where no loader ticks. */
    @WrapOperation(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/LoadingChunkTracker;runDistanceUpdates(I)I"))
    private int leafs$drainAuthorities(LoadingChunkTracker tracker, int toProcess, Operation<Integer> original) {
        leafs$propagator.drain();
        leafs$simulation.drain();

        return original.call(tracker, toProcess);
    }

    @Inject(method = "hasPlayersNearby", at = @At("HEAD"), cancellable = true)
    private void leafs$directSpawnProximity(long pos, CallbackInfoReturnable<TriState> callbackInfo) {
        callbackInfo.setReturnValue(leafs$spawnProximity.nearby(pos));
    }

    @Inject(method = "getNaturalSpawnChunkCount", at = @At("HEAD"), cancellable = true)
    private void leafs$directSpawnChunkCount(CallbackInfoReturnable<Integer> callbackInfo) {
        callbackInfo.setReturnValue(leafs$spawnProximity.coveredCount());
    }

    @Inject(method = "getSpawnCandidateChunks", at = @At("HEAD"), cancellable = true)
    private void leafs$directSpawnCandidates(CallbackInfoReturnable<LongIterator> callbackInfo) {
        callbackInfo.setReturnValue(leafs$spawnProximity.coveredChunks());
    }

    @Inject(method = "inEntityTickingRange", at = @At("HEAD"), cancellable = true)
    private void leafs$shardedEntityTickingRange(long key, CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(ChunkLevel.isEntityTicking(leafs$simulation.level(key)));
    }

    @Inject(method = "inBlockTickingRange", at = @At("HEAD"), cancellable = true)
    private void leafs$shardedBlockTickingRange(long key, CallbackInfoReturnable<Boolean> callbackInfo) {
        callbackInfo.setReturnValue(ChunkLevel.isBlockTicking(leafs$simulation.level(key)));
    }

    @Inject(method = "getChunkLevel", at = @At("HEAD"), cancellable = true)
    private void leafs$shardedSimulationLevel(long key, boolean simulation, CallbackInfoReturnable<Integer> callbackInfo) {
        if (simulation) {
            callbackInfo.setReturnValue(leafs$simulation.level(key));
        }
    }
}
