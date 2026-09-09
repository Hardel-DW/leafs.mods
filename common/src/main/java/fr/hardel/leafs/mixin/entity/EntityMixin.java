package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.EntityTickAccess;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

// Entity hooks: every move and every portal search leave through the teleport funnel, whatever thread is running; one thread ticks an entity at a time.
@Mixin(Entity.class)
public abstract class EntityMixin implements EntityTickAccess {
    @Unique
    private final AtomicBoolean leafs$ticking = new AtomicBoolean();

    @Override
    public boolean leafs$beginTick() {
        return leafs$ticking.compareAndSet(false, true);
    }

    @Override
    public void leafs$endTick() {
        leafs$ticking.set(false);
    }


    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void leafs$divertOffOwnerTeleport(TeleportTransition transition, CallbackInfoReturnable<Entity> callbackInfo) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer) {
            return;
        }

        if (self.level() instanceof ServerLevel origin && !self.isRemoved()
            && ((ServerLevelEntityAccess) origin).leafs$entityTeleports().route(self, transition)) {
            callbackInfo.setReturnValue(transition.newLevel() == origin ? self : null);
        }
    }

    // The search and the teleport leave through the funnel; vanilla gets no destination here and does nothing more.
    @WrapOperation(method = "handlePortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PortalProcessor;getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition leafs$portalThroughTheFunnel(PortalProcessor processor, ServerLevel level, Entity entity, Operation<TeleportTransition> original) {
        ((ServerLevelEntityAccess) level).leafs$entityTeleports().deferPortal(entity, processor);
        return null;
    }
}
