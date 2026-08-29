package fr.hardel.leafs.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.PlayerLoaderAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** The level-wide tick list and mob set stay empty, a region reads its entities from the sections of its chunks; a player landing in a chunk simulates it at once, whatever put him there. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksMixin {

    @Inject(method = {"onTickingStart(Lnet/minecraft/world/entity/Entity;)V", "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V"}, at = @At("HEAD"), cancellable = true)
    private void leafs$noLevelTickList(Entity entity, CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }

    /** Added to the level or moved across a section: the two ways a player lands in a chunk, whatever put him there. */
    @Inject(method = {"onCreated(Lnet/minecraft/world/entity/Entity;)V", "onSectionChange(Lnet/minecraft/world/entity/Entity;)V"}, at = @At("HEAD"))
    private void leafs$followThePlayer(Entity entity, CallbackInfo callbackInfo) {
        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            ((PlayerLoaderAccess) level.getChunkSource().chunkMap).leafs$playerLoader().follow(player);
        }
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
