package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Entity hooks: every move and every portal search leave through the teleport funnel, whatever thread is running.
@Mixin(Entity.class)
public abstract class EntityMixin {

    // Every thread routes, including a mod's own pool: the funnel replays vanilla in place when the caller already holds the destination.
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void leafs$divertOffOwnerTeleport(TeleportTransition transition, CallbackInfoReturnable<Entity> callbackInfo) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer) {
            return;
        }

        if (self.level() instanceof ServerLevel origin && !self.isRemoved()
            && ((ServerLevelEntityAccess) origin).leafs$entityTeleports().route(self, transition)) {
            callbackInfo.setReturnValue(null);
        }
    }

    // The search writes foreign-dimension blocks, so it belongs to the window; the window replays it in place when it is already the caller.
    @WrapOperation(method = "handlePortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PortalProcessor;getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition leafs$deferPortalSearchOffOwner(PortalProcessor processor, ServerLevel level, Entity entity, Operation<TeleportTransition> original) {
        if (!((ServerLevelEntityAccess) level).leafs$entityTeleports().deferPortal(entity, processor)) {
            return original.call(processor, level, entity);
        }

        return null;
    }
}
