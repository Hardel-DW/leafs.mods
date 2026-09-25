package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.entity.PlayerMoveAccess;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.ticking.TickingManager;
import fr.hardel.leafs.world.SavedEpochAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

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

    @WrapOperation(method = "adjustSpawnLocation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void leafs$spawnSearchWaits(MinecraftServer server, BooleanSupplier done, Operation<Void> original, @Local(argsOnly = true) ServerLevel level) {
        TickingManager.of(server).await(done);
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
    private void leafs$deferOffOwnerPlayerMove(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callbackInfo) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!(self.level() instanceof ServerLevel origin)) {
            return;
        }

        if (!self.isRemoved() && ((ServerLevelEntityAccess) origin).leafs$entityTeleports().route(self, transition)) {
            callbackInfo.setReturnValue(self);
        }
    }
}
