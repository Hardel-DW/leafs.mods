package fr.hardel.leafs.mixin.compat.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** Direwolf20-MC/JustDireThings, fluid craft cache. Item entities fill the cache from their region. */
@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.common.events.EntityEvents")
public abstract class EntityEventsMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedFluidCraftCache(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
