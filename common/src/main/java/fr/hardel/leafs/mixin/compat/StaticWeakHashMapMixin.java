package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedWeakHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.WeakHashMap;

@Pseudo
@Mixin(targets = {
    // Team-EnderIO/EnderIO, hang glider falling ticks, written by every player tick from the player's region.
    "com.enderio.enderio.content.tools.hang_glider.PlayerMovementHandler"
})
public abstract class StaticWeakHashMapMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/WeakHashMap"))
    private static <K, V> WeakHashMap<K, V> leafs$synchronized(Operation<WeakHashMap<K, V>> original) {
        return new SynchronizedWeakHashMap<>();
    }
}
