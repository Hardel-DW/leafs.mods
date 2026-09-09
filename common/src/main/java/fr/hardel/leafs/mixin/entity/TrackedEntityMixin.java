package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.TrackedPairingRefresh;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The watcher set is written by the tracking pass of every region that owns one of the watchers. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {

    @Mutable
    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.seenBy = ConcurrentHashMap.newKeySet();
    }

    /** A first watcher re-anchors the base: only then is there no delta stream the refresh could tear. */
    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity;addPairing(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void leafs$freshBaseOnFirstPairing(ServerEntity serverEntity, ServerPlayer player, Operation<Void> original) {
        if (this.seenBy.size() == 1) {
            ((TrackedPairingRefresh) serverEntity).leafs$refreshPairingBase();
        }

        original.call(serverEntity, player);
    }
}
