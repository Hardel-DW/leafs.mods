package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.chunk.SavedEpochAccess;
import fr.hardel.leafs.entity.PlayerMoveAccess;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
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
