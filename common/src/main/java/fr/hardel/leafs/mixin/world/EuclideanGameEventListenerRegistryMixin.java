package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.world.GameEventListeners;
import net.minecraft.core.Holder;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EuclideanGameEventListenerRegistry.class)
public abstract class EuclideanGameEventListenerRegistryMixin {
    @Shadow
    @Final
    @Mutable
    private List<GameEventListener> listeners;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$snapshotListeners(CallbackInfo callbackInfo) {
        GameEventListeners concurrent = new GameEventListeners();
        concurrent.addAll(this.listeners);
        this.listeners = concurrent;
    }

    @ModifyExpressionValue(method = { "register", "unregister" },
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/gameevent/EuclideanGameEventListenerRegistry;processing:Z"))
    private boolean leafs$listOwnsPendingChanges(boolean processing) {
        return false;
    }

    @WrapMethod(method = "visitInRangeListeners")
    private boolean leafs$independentVisit(Holder<GameEvent> event, Vec3 source, GameEvent.Context context, GameEventListenerRegistry.ListenerVisitor visitor,
        Operation<Boolean> original) {
        GameEventListeners concurrent = (GameEventListeners) this.listeners;
        concurrent.beginVisit();
        try {
            return original.call(event, source, context, visitor);
        } finally {
            concurrent.endVisit();
        }
    }
}
