package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/** The level-wide tick list and mob set stay empty, a region reads its entities from the sections of its chunks. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksMixin {

    @WrapOperation(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;add(Lnet/minecraft/world/entity/Entity;)V"))
    private void leafs$noLevelTickList(EntityTickList instance, Entity entity, Operation<Void> original) {
    }

    @WrapOperation(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;remove(Lnet/minecraft/world/entity/Entity;)V"))
    private void leafs$noLevelTickListRemoval(EntityTickList instance, Entity entity, Operation<Void> original) {
    }

    @WrapOperation(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean leafs$noLevelMobSet(Set<Mob> instance, Object mob, Operation<Boolean> original) {
        return true;
    }

    @WrapOperation(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z"))
    private boolean leafs$noLevelMobSetRemoval(Set<Mob> instance, Object mob, Operation<Boolean> original) {
        return true;
    }
}
