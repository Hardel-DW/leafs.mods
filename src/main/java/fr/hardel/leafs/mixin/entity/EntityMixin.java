package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.entity.ServerEntityAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only - logic in entity/EntitySchedulerRegistry: it decides which removals retire the scheduler. */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void leafs$retireSchedulerOnRemoval(Entity.RemovalReason reason, CallbackInfo callbackInfo) {
        Entity self = (Entity) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            ((ServerEntityAccess) level.getServer()).leafs$entitySchedulers().onEntityRemoved(self, reason);
        }
    }
}
