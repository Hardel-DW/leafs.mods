package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedIdentityHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.IdentityHashMap;

@Pseudo
@Mixin(targets = {
    // Shadows-of-Fire/FastWorkbench, slot updates, queued by crafting from the player's region and run and cleared by the server tick.
    "dev.shadowsoffire.fastbench.util.SlotUpdateManager"
})
public abstract class StaticIdentityHashMapMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/IdentityHashMap"))
    private static <K, V> IdentityHashMap<K, V> leafs$synchronized(Operation<IdentityHashMap<K, V>> original) {
        return new SynchronizedIdentityHashMap<>();
    }
}
