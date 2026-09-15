package fr.hardel.leafs.mixin.compat.mahoutsukai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** stepsword/mahoutsukai, tag subset cache. isSubset fills the cache from the casting player's region. */
@Pseudo
@Mixin(targets = "stepsword.mahoutsukai.effects.projection.StrengtheningSpellEffect")
public abstract class StrengtheningSpellEffectMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedCache(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
