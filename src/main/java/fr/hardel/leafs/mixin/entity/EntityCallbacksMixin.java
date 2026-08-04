package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.entity.LevelEntityLists;
import fr.hardel.leafs.entity.ServerLevelEntityAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Binds the section callbacks to the per-region lists: the level-wide entity tick list and
 * navigating-mob set stay empty from tick zero, which is what shrinks the serial remainder's loops
 * to no-ops without touching the tick body.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksMixin {

    @Inject(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void leafs$tickListToOwningUnit(Entity entity, CallbackInfo callbackInfo) {
        leafs$lists(entity).tickingStarted(entity);
        callbackInfo.cancel();
    }

    @Inject(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void leafs$tickListRemoveFromOwningUnit(Entity entity, CallbackInfo callbackInfo) {
        leafs$lists(entity).tickingEnded(entity);
        callbackInfo.cancel();
    }

    @WrapOperation(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean leafs$navigationToOwningUnit(Set<Mob> instance, Object mob, Operation<Boolean> original) {
        leafs$lists((Mob) mob).navigationStarted((Mob) mob);

        return true;
    }

    @WrapOperation(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z"))
    private boolean leafs$navigationRemoveFromOwningUnit(Set<Mob> instance, Object mob, Operation<Boolean> original) {
        leafs$lists((Mob) mob).navigationEnded((Mob) mob);

        return true;
    }

    @Unique
    private static LevelEntityLists leafs$lists(Entity entity) {
        return ((ServerLevelEntityAccess) (ServerLevel) entity.level()).leafs$entityLists();
    }
}
