package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.ServerEntityAccess;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ticking.LevelBindings;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity-side hooks: scheduler retirement (entity/EntitySchedulerRegistry), the #16 teleport
 * diversion off the owning region, the portal-search deferral to the level-serial side, and the
 * portal ticket that must reach the level's single ticket mutator.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void leafs$retireSchedulerOnRemoval(Entity.RemovalReason reason, CallbackInfo callbackInfo) {
        Entity self = (Entity) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            ((ServerEntityAccess) level.getServer()).leafs$entitySchedulers().onEntityRemoved(self, reason);
        }
    }

    /** Sync teleports stay valid inside the owning region; everything else routes (Compromise #7). */
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void leafs$divertOffOwnerTeleport(TeleportTransition transition, CallbackInfoReturnable<Entity> callbackInfo) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer || !(RegionContext.current() instanceof RegionContext.Region)) {
            return;
        }

        if (self.level() instanceof ServerLevel origin && !self.isRemoved()
            && ((ServerLevelEntityAccess) origin).leafs$entityTeleports().divertFromRegion(self, transition)) {
            callbackInfo.setReturnValue(null);
        }
    }

    /** The destination search sync-loads foreign chunks; off the serial side it defers whole (#18's verdict). */
    @WrapOperation(method = "handlePortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PortalProcessor;getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition leafs$deferPortalSearchOffOwner(PortalProcessor processor, ServerLevel level, Entity entity, Operation<TeleportTransition> original) {
        if (!(RegionContext.current() instanceof RegionContext.Region)) {
            return original.call(processor, level, entity);
        }

        ((ServerLevelEntityAccess) level).leafs$entityTeleports().deferPortal(entity);

        return null;
    }

    @WrapOperation(method = "placePortalTicket", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addTicketWithRadius(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;I)V"))
    private void leafs$deferPortalTicket(ServerChunkCache chunkSource, TicketType type, ChunkPos pos, int radius, Operation<Void> original) {
        LevelBindings.addTicketWithRadius(chunkSource, type, pos, radius);
    }
}
