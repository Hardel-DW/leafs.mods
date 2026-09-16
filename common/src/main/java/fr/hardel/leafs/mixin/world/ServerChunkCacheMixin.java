package fr.hardel.leafs.mixin.world;

import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mutable;
import fr.hardel.leafs.chunk.ChangedChunksAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.PlayerMoveAccess;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.LevelRegions;

import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.RegionTickBody;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Per-chunk tick work moved to region bodies; broadcast marks route to the owning unit. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin implements ChangedChunksAccess {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    private boolean spawnEnemies;

    @Mutable
    @Shadow
    @Final
    private Set<ChunkHolder> chunkHoldersToBroadcast;

    @Override
    public Set<ChunkHolder> leafs$changedHolders() {
        return chunkHoldersToBroadcast;
    }

    /** The set takes writers from every owner; a region removes what it broadcasts, the workers' sweep drains the rest. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$bindPumpLevel(CallbackInfo callbackInfo) {
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        ((ChunkPumpAccess) (Object) self.mainThreadProcessor).leafs$bindLevel(this.level);
        this.chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();
    }

    /** Once regions tick, each purges its own sections and the workers' sweep the rest; the halted case matters, vanilla keeps purging during its stop loop. */
    @WrapOperation(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/TicketStorage;purgeStaleTickets(Lnet/minecraft/server/level/ChunkMap;)V"))
    private void leafs$purgeAsUniversalOwner(TicketStorage storage, ChunkMap chunkMap, Operation<Void> original) {
        if (!LevelRegions.of(this.level).live()) {
            original.call(storage, chunkMap);
        }
    }

    @WrapOperation(method = "tickChunks()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V"))
    private void leafs$serialChunkTickRemainder(ServerChunkCache instance, ProfilerFiller profiler, Operation<Void> original) {
        RegionTickBody body = LevelRegions.of(this.level).body();
        if (body == null) {
            original.call(instance, profiler);

            return;
        }

        body.tickSerial(this.spawnEnemies);
        TickingManager.of(this.level.getServer()).markSerial(this.level, TickStages.serialView);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z", shift = At.Shift.AFTER), require = 0)
    private void leafs$markPurgeStage(CallbackInfo callbackInfo) {
        TickingManager.of(this.level.getServer()).markSerial(this.level, TickStages.serialPurge);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;tick(Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.AFTER), require = 0)
    private void leafs$markUnloadsStage(CallbackInfo callbackInfo) {
        TickingManager.of(this.level.getServer()).markSerial(this.level, TickStages.serialUnloads);
    }

    /** The serial broadcast walk only survives for the universal owner; after activation the sweep owns the set. */
    @WrapOperation(method = "tickChunks()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;broadcastChangedChunks(Lnet/minecraft/util/profiling/ProfilerFiller;)V"))
    private void leafs$broadcastAsUniversalOwner(ServerChunkCache instance, ProfilerFiller profiler, Operation<Void> original) {
        if (!LevelRegions.of(this.level).live()) {
            original.call(instance, profiler);
        }
    }

    /** The move runs where it is called; the visibility pass it used to carry runs on every region's tracking tick. */
    @Inject(method = "move", at = @At("HEAD"))
    private void leafs$markPlayerMoved(ServerPlayer player, CallbackInfo callbackInfo) {
        ((PlayerMoveAccess) player).leafs$markMoved();
    }
}
