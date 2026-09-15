package fr.hardel.leafs.mixin.compat.fastbench;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedIdentityHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.IdentityHashMap;

/** Shadows-of-Fire/FastWorkbench, slot updates. Crafting queues an update from the player's region; the server tick runs and clears them. */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.fastbench.util.SlotUpdateManager")
public abstract class SlotUpdateManagerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/IdentityHashMap"))
    private static <K, V> IdentityHashMap<K, V> leafs$sharedUpdates(Operation<IdentityHashMap<K, V>> original) {
        return new SynchronizedIdentityHashMap<>();
    }
}
