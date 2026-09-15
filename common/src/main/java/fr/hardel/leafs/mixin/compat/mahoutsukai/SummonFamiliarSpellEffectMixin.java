package fr.hardel.leafs.mixin.compat.mahoutsukai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.SynchronizedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

/** stepsword/mahoutsukai, familiars. Summons and the familiar's own tick write the map from their regions. */
@Pseudo
@Mixin(targets = "stepsword.mahoutsukai.effects.familiar.SummonFamiliarSpellEffect")
public abstract class SummonFamiliarSpellEffectMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", target = "java/util/HashMap"))
    private static <K, V> HashMap<K, V> leafs$sharedFamiliarMap(Operation<HashMap<K, V>> original) {
        return new SynchronizedHashMap<>();
    }
}
