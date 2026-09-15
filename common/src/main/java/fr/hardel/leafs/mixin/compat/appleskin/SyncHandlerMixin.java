package fr.hardel.leafs.mixin.compat.appleskin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** squeek502/AppleSkin, per player levels. Every player tick writes the maps from the player's region. */
@Pseudo
@Mixin(targets = "squeek.appleskin.network.SyncHandler")
public abstract class SyncHandlerMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedLevels(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
