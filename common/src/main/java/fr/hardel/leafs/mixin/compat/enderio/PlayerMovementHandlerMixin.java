package fr.hardel.leafs.mixin.compat.enderio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedWeakHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.WeakHashMap;

/** Team-EnderIO/EnderIO, hang glider. Every player tick writes the falling ticks from the player's region. */
@Pseudo
@Mixin(targets = "com.enderio.enderio.content.tools.hang_glider.PlayerMovementHandler")
public abstract class PlayerMovementHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/WeakHashMap"))
    private static <K, V> WeakHashMap<K, V> leafs$sharedTicksFalling(Operation<WeakHashMap<K, V>> original) {
        return new SynchronizedWeakHashMap<>();
    }
}
