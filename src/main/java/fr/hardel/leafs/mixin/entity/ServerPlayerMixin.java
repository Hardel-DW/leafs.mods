package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.chunk.SavedEpochAccess;
import fr.hardel.leafs.entity.PlayerMoveAccess;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.entity.SpawnSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Moves route through the teleport funnel; the pearl set goes concurrent for cross-region registration. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements PlayerMoveAccess, SavedEpochAccess {

    @Unique
    private volatile long leafs$movedNanos;

    @Unique
    private long leafs$savedEpoch;

    @Mutable
    @Shadow
    @Final
    private Set<ThrownEnderpearl> enderPearls;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPearlSet(CallbackInfo callbackInfo) {
        this.enderPearls = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void leafs$markMoved() {
        leafs$movedNanos = System.nanoTime();
    }

    @Override
    public long leafs$movedNanos() {
        return leafs$movedNanos;
    }

    @Override
    public long leafs$savedEpoch() {
        return leafs$savedEpoch;
    }

    @Override
    public void leafs$markSaved(long epoch) {
        leafs$savedEpoch = epoch;
    }

    @Unique
    private final SpawnSearch leafs$spawnSearch = new SpawnSearch();

    /** A replayed move resumes the search it refused on, instead of starting one per attempt. */
    @WrapOperation(method = "adjustSpawnLocation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/PlayerSpawnFinder;findSpawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Vec3> leafs$resumeSpawnSearch(ServerLevel level, BlockPos spawnSuggestion, Operation<CompletableFuture<Vec3>> original) {
        return leafs$spawnSearch.resume(level, spawnSuggestion, () -> original.call(level, spawnSuggestion));
    }

    /** The server thread waits as vanilla; a region refuses with the search as readiness and the deferred move replays once found. */
    @WrapOperation(method = "adjustSpawnLocation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void leafs$spawnSearchOnTheOwner(MinecraftServer server, BooleanSupplier done, Operation<Void> original, @Local(argsOnly = true) ServerLevel level, @Local CompletableFuture<Vec3> search) {
        leafs$spawnSearch.await(level, search);
    }

    // Every thread routes, including a mod's own pool: the funnel replays vanilla in place when the caller already holds the destination.
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
    private void leafs$deferOffOwnerPlayerMove(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callbackInfo) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!(self.level() instanceof ServerLevel origin)) {
            return;
        }

        if (!self.isRemoved() && ((ServerLevelEntityAccess) origin).leafs$entityTeleports().route(self, transition)) {
            callbackInfo.setReturnValue(null);
        }
    }
}
