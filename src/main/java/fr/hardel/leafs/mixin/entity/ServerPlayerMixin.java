package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ticking.LevelBindings;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Player moves from region workers defer to the level-serial side. Pearl set goes concurrent for cross-region registration. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Mutable
    @Shadow
    @Final
    private Set<ThrownEnderpearl> enderPearls;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentPearlSet(CallbackInfo callbackInfo) {
        this.enderPearls = ConcurrentHashMap.newKeySet();
    }

    /** Region workers only: the serial side runs vanilla inline, a wider gate would re-divert its own deferred tasks forever. */
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
    private void leafs$deferOffOwnerPlayerMove(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callbackInfo) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!(RegionContext.current() instanceof RegionContext.Region) || !(self.level() instanceof ServerLevel origin)) {
            return;
        }

        if (((ServerLevelEntityAccess) origin).leafs$entityTeleports().divertPlayerFromRegion(self, transition)) {
            callbackInfo.setReturnValue(null);
        }
    }

    @WrapOperation(method = "placeEnderPearlTicket", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addTicketWithRadius(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;I)V"))
    private static void leafs$deferPearlTicket(ServerChunkCache chunkSource, TicketType type, ChunkPos pos, int radius, Operation<Void> original) {
        LevelBindings.addTicketWithRadius(chunkSource, type, pos, radius);
    }
}
