package fr.hardel.leafs.mixin.compat.morered;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** Commoble/morered, shape cache. getShape fills the cache from whichever thread asks for a shape. */
@Pseudo
@Mixin(targets = "net.commoble.morered.mechanisms.GearsBlock")
public abstract class GearsBlockMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedShapeCaches(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
